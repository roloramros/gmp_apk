// src/controllers/jobsController.js
//
// CRUD base de jobs: crear, listar/buscar, detalle, actualizar.
// Las acciones de estado (start/finish/invoice/pay/cancel/assign/unassign)
// viven en un controller aparte (jobsActionsController), fase siguiente.
//
// Visibilidad por rol (cerrado en fase1-diseno-datos-sync.md, sección 5):
//   admin / comercial -> todos los jobs de la empresa
//   trabajador        -> solo jobs donde está en job_workers (activo)
//   cliente           -> solo jobs.client_id = self
//
// NOTA (Fase 6 — réplica del modelo de la web legada, index.html):
// se agregaron 11 campos de "montaje" (datos de cliente sueltos, ubicación,
// precio, forma de pago y fechas) que no existían en el diseño original de
// jobs. `title` pasó a ser opcional: si no llega, se autocompleta con
// client_name, para no romper nada que dependa de title (command_log, sync).
const pool = require('../db/pool');
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

// SELECT base reutilizado en listar/buscar y detalle.
// Incluye datos de cliente (join) y array de trabajadores asignados (subquery).
const BASE_SELECT = `
  SELECT
    j.uuid, j.title, j.description, j.status, j.address,
    j.scheduled_at, j.started_at, j.finished_at, j.invoiced_at,
    j.total_amount, j.amount_paid, j.cancelled_at,
    j.client_name, j.client_ci, j.client_phone,
    j.latitude, j.longitude, j.reference, j.site_notes,
    j.price, j.payment_method,
    to_char(j.visit_date, 'YYYY-MM-DD') AS visit_date,
    to_char(j.proposed_date, 'YYYY-MM-DD') AS proposed_date,
    j.created_at, j.updated_at,
    cu.uuid AS client_uuid, cu.full_name AS client_user_name,
    cb.uuid AS created_by_uuid, cb.full_name AS created_by_name,
    COALESCE(
      (SELECT json_agg(json_build_object('uuid', wu.uuid, 'full_name', wu.full_name))
       FROM job_workers jw
       JOIN users wu ON wu.id = jw.user_id
       WHERE jw.job_id = j.id AND jw.deleted_at IS NULL),
      '[]'
    ) AS workers,
    COALESCE(
      (SELECT json_agg(json_build_object(
         'uuid', jm.uuid,
         'material_uuid', m.uuid,
         'material_name', m.name,
         'free_text_description', jm.free_text_description,
         'quantity', jm.quantity,
         'unit_price', jm.unit_price
       ))
       FROM job_materials jm
       LEFT JOIN materials m ON m.id = jm.material_id
       WHERE jm.job_id = j.id AND jm.deleted_at IS NULL),
      '[]'
    ) AS materials,
    COALESCE(
      (SELECT json_agg(json_build_object(
         'uuid', jp.uuid,
         'uploaded_by_uuid', puu.uuid,
         'uploaded_by_name', puu.full_name,
         'url', format('/jobs/%s/photos/%s/file', j.uuid, jp.uuid),
         'created_at', jp.created_at
       ))
       FROM job_photos jp
       JOIN users puu ON puu.id = jp.uploaded_by_user_id
       WHERE jp.job_id = j.id AND jp.deleted_at IS NULL),
      '[]'
    ) AS photos
  FROM jobs j
  LEFT JOIN users cu ON cu.id = j.client_id
  JOIN users cb ON cb.id = j.created_by_user_id
`;

// Devuelve el fragmento SQL de visibilidad por rol + el/los parámetros que necesita,
// empezando en el índice $paramOffset. Se usa tanto en listar como en detalle.
function roleVisibilityClause(user, paramOffset) {
  if (user.role === 'trabajador') {
    return {
      clause: `AND EXISTS (
        SELECT 1 FROM job_workers jw2
        WHERE jw2.job_id = j.id AND jw2.user_id = $${paramOffset} AND jw2.deleted_at IS NULL
      )`,
      params: [user.user_id],
    };
  }
  if (user.role === 'cliente') {
    return {
      clause: `AND j.client_id = $${paramOffset}`,
      params: [user.user_id],
    };
  }
  // admin / comercial: sin restricción adicional
  return { clause: '', params: [] };
}

// Resuelve un client_uuid al id interno de un user con role='cliente' de la misma empresa.
async function resolveClientId(clientUuid, companyId) {
  if (!clientUuid) return { id: null, error: null };
  if (!UUID_RE.test(clientUuid)) {
    return { id: null, error: { error_code: 'invalid_client_uuid', message: 'client_uuid inválido.' } };
  }
  const result = await pool.query(
    `SELECT id FROM users WHERE uuid = $1 AND company_id = $2 AND role = 'cliente' AND deleted_at IS NULL`,
    [clientUuid, companyId]
  );
  if (result.rows.length === 0) {
    return { id: null, error: { error_code: 'client_not_found', message: 'No se encontró un cliente con ese client_uuid en la empresa.' } };
  }
  return { id: result.rows[0].id, error: null };
}

// Valida y normaliza los campos de "montaje" nuevos, comunes a create y update.
// Devuelve { values, error }. `values` trae solo las claves presentes en el body
// (para poder distinguir "no mandado" de "mandado como null" en el update).
const MONTAJE_FIELDS = [
  'client_name', 'client_ci', 'client_phone',
  'latitude', 'longitude', 'reference', 'site_notes',
  'price', 'payment_method', 'visit_date', 'proposed_date',
];

function validateMontajeFields(body, { requireCore }) {
  const values = {};

  if (Object.prototype.hasOwnProperty.call(body, 'client_name')) {
    const v = typeof body.client_name === 'string' ? body.client_name.trim() : '';
    if (requireCore && !v) {
      return { error: { error_code: 'invalid_client_name', message: 'client_name es requerido.' } };
    }
    values.client_name = v || null;
  } else if (requireCore) {
    return { error: { error_code: 'invalid_client_name', message: 'client_name es requerido.' } };
  }

  if (Object.prototype.hasOwnProperty.call(body, 'client_ci')) {
    values.client_ci = body.client_ci ? String(body.client_ci).trim() : null;
  }
  if (Object.prototype.hasOwnProperty.call(body, 'client_phone')) {
    values.client_phone = body.client_phone ? String(body.client_phone).trim() : null;
  }
  if (Object.prototype.hasOwnProperty.call(body, 'reference')) {
    values.reference = body.reference ? String(body.reference).trim() : null;
  }
  if (Object.prototype.hasOwnProperty.call(body, 'site_notes')) {
    values.site_notes = body.site_notes ? String(body.site_notes).trim() : null;
  }
  if (Object.prototype.hasOwnProperty.call(body, 'payment_method')) {
    values.payment_method = body.payment_method ? String(body.payment_method).trim() : null;
  }

  if (Object.prototype.hasOwnProperty.call(body, 'latitude')) {
    const v = body.latitude;
    if (v !== null && v !== undefined && v !== '' && (typeof v !== 'number' || Number.isNaN(v))) {
      return { error: { error_code: 'invalid_latitude', message: 'latitude debe ser numérico.' } };
    }
    values.latitude = v === '' || v === undefined ? null : v;
  }
  if (Object.prototype.hasOwnProperty.call(body, 'longitude')) {
    const v = body.longitude;
    if (v !== null && v !== undefined && v !== '' && (typeof v !== 'number' || Number.isNaN(v))) {
      return { error: { error_code: 'invalid_longitude', message: 'longitude debe ser numérico.' } };
    }
    values.longitude = v === '' || v === undefined ? null : v;
  }

  if (Object.prototype.hasOwnProperty.call(body, 'price')) {
    const v = body.price;
    const num = typeof v === 'number' ? v : parseFloat(v);
    if (requireCore && (v === null || v === undefined || v === '' || Number.isNaN(num) || num < 0)) {
      return { error: { error_code: 'invalid_price', message: 'price es requerido y debe ser un número >= 0.' } };
    }
    if (!requireCore && v !== null && v !== undefined && v !== '' && (Number.isNaN(num) || num < 0)) {
      return { error: { error_code: 'invalid_price', message: 'price debe ser un número >= 0.' } };
    }
    values.price = (v === null || v === undefined || v === '') ? null : num;
  } else if (requireCore) {
    return { error: { error_code: 'invalid_price', message: 'price es requerido.' } };
  }

  for (const field of ['visit_date', 'proposed_date']) {
    if (Object.prototype.hasOwnProperty.call(body, field)) {
      const v = body[field];
      if (v !== null && v !== undefined && v !== '' && !DATE_RE.test(v)) {
        return { error: { error_code: `invalid_${field}`, message: `${field} debe tener formato YYYY-MM-DD.` } };
      }
      values[field] = v || null;
    }
  }

  return { values, error: null };
}

// POST /jobs
// body: { uuid, title?, description?, address, scheduled_at?, client_uuid?, created_by_device_id?,
//         client_name, client_ci?, client_phone?, latitude?, longitude?, reference?, site_notes?,
//         price, payment_method?, visit_date?, proposed_date? }
async function createJob(req, res) {
  const {
    uuid, title, description, address, scheduled_at, client_uuid, created_by_device_id,
  } = req.body || {};

  if (!uuid || !UUID_RE.test(uuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid es requerido y debe ser un UUID válido.' });
  }
  if (!address || typeof address !== 'string' || !address.trim()) {
    return res.status(400).json({ error_code: 'invalid_address', message: 'address es requerido.' });
  }

  const { values: montaje, error: montajeError } = validateMontajeFields(req.body || {}, { requireCore: true });
  if (montajeError) {
    return res.status(400).json(montajeError);
  }

  const finalTitle = (title && String(title).trim()) || montaje.client_name;

  const { id: clientId, error: clientError } = await resolveClientId(client_uuid, req.user.company_id);
  if (clientError) {
    return res.status(400).json(clientError);
  }

  try {
    const insertResult = await pool.query(
      `INSERT INTO jobs
         (uuid, company_id, client_id, created_by_user_id, title, description, address, scheduled_at,
          created_by_device_id, client_name, client_ci, client_phone, latitude, longitude,
          reference, site_notes, price, payment_method, visit_date, proposed_date)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20)
       RETURNING uuid`,
      [
        uuid,
        req.user.company_id,
        clientId,
        req.user.user_id,
        finalTitle,
        description || null,
        address.trim(),
        scheduled_at || null,
        created_by_device_id || null,
        montaje.client_name,
        montaje.client_ci ?? null,
        montaje.client_phone ?? null,
        montaje.latitude ?? null,
        montaje.longitude ?? null,
        montaje.reference ?? null,
        montaje.site_notes ?? null,
        montaje.price,
        montaje.payment_method ?? null,
        montaje.visit_date ?? null,
        montaje.proposed_date ?? null,
      ]
    );
    const fullResult = await pool.query(`${BASE_SELECT} WHERE j.uuid = $1`, [insertResult.rows[0].uuid]);
    return res.status(201).json(fullResult.rows[0]);
  } catch (err) {
    if (err.code === '23505') {
      return res.status(409).json({ error_code: 'uuid_conflict', message: 'Ya existe un job con ese uuid.' });
    }
    console.error('[jobs] Error en createJob:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al crear el job.' });
  }
}

// GET /jobs?q=&status=&limit=&offset=
async function listJobs(req, res) {
  const { q, status, limit, offset } = req.query;
  const pageLimit = Math.min(parseInt(limit, 10) || 50, 200);
  const pageOffset = parseInt(offset, 10) || 0;
  const params = [req.user.company_id];
  let where = 'WHERE j.company_id = $1 AND j.deleted_at IS NULL';
  if (status) {
    params.push(status);
    where += ` AND j.status = $${params.length}`;
  }
  if (q && q.trim()) {
    params.push(`%${q.trim()}%`);
    where += ` AND (j.title ILIKE $${params.length} OR j.description ILIKE $${params.length} OR j.address ILIKE $${params.length} OR j.client_name ILIKE $${params.length})`;
  }
  const visibility = roleVisibilityClause(req.user, params.length + 1);
  where += ` ${visibility.clause}`;
  params.push(...visibility.params);
  params.push(pageLimit, pageOffset);
  try {
    const result = await pool.query(
      `${BASE_SELECT}
       ${where}
       ORDER BY j.created_at DESC
       LIMIT $${params.length - 1} OFFSET $${params.length}`,
      params
    );
    return res.status(200).json({ jobs: result.rows });
  } catch (err) {
    console.error('[jobs] Error en listJobs:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al listar jobs.' });
  }
}

// GET /jobs/:uuid
async function getJob(req, res) {
  const { uuid } = req.params;
  if (!UUID_RE.test(uuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  }
  const params = [req.user.company_id, uuid];
  let where = 'WHERE j.company_id = $1 AND j.deleted_at IS NULL AND j.uuid = $2';
  const visibility = roleVisibilityClause(req.user, params.length + 1);
  where += ` ${visibility.clause}`;
  params.push(...visibility.params);
  try {
    const result = await pool.query(`${BASE_SELECT} ${where}`, params);
    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Job no encontrado.' });
    }
    return res.status(200).json(result.rows[0]);
  } catch (err) {
    console.error('[jobs] Error en getJob:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al obtener el job.' });
  }
}

// PATCH /jobs/:uuid
// body: campos editables { title?, description?, address?, scheduled_at?, client_uuid?,
//        client_name?, client_ci?, client_phone?, latitude?, longitude?, reference?,
//        site_notes?, price?, payment_method?, visit_date?, proposed_date? }
const EDITABLE_FIELDS = ['title', 'description', 'address', 'scheduled_at'];

async function updateJob(req, res) {
  const { uuid } = req.params;
  const body = req.body || {};
  if (!UUID_RE.test(uuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  }

  const fieldsToUpdate = EDITABLE_FIELDS.filter((f) => Object.prototype.hasOwnProperty.call(body, f));
  const updatingClient = Object.prototype.hasOwnProperty.call(body, 'client_uuid');

  const { values: montaje, error: montajeError } = validateMontajeFields(body, { requireCore: false });
  if (montajeError) {
    return res.status(400).json(montajeError);
  }
  const montajeKeys = Object.keys(montaje);

  if (fieldsToUpdate.length === 0 && !updatingClient && montajeKeys.length === 0) {
    return res.status(400).json({
      error_code: 'no_fields',
      message: 'No se enviaron campos editables.',
    });
  }

  let clientId;
  if (updatingClient) {
    const { id, error } = await resolveClientId(body.client_uuid, req.user.company_id);
    if (error) return res.status(400).json(error);
    clientId = id; // puede ser null si se quiere quitar el cliente (client_uuid: null)
  }

  const allFieldNames = [...fieldsToUpdate, ...montajeKeys];
  const setClauses = allFieldNames.map((f, i) => `${f} = $${i + 1}`);
  const values = [...fieldsToUpdate.map((f) => body[f]), ...montajeKeys.map((f) => montaje[f])];

  if (updatingClient) {
    setClauses.push(`client_id = $${values.length + 1}`);
    values.push(clientId);
  }

  try {
    const result = await pool.query(
      `UPDATE jobs
       SET ${setClauses.join(', ')}, updated_at = now()
       WHERE uuid = $${values.length + 1} AND company_id = $${values.length + 2} AND deleted_at IS NULL
       RETURNING uuid`,
      [...values, uuid, req.user.company_id]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Job no encontrado.' });
    }
    const fullResult = await pool.query(`${BASE_SELECT} WHERE j.uuid = $1`, [uuid]);
    return res.status(200).json(fullResult.rows[0]);
  } catch (err) {
    console.error('[jobs] Error en updateJob:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al actualizar el job.' });
  }
}

// Reutilizado por jobsActionsController.js para devolver el job completo
// con el mismo formato después de cada acción (assign, start, pay, etc).
async function getFullJobByUuid(uuid) {
  const result = await pool.query(`${BASE_SELECT} WHERE j.uuid = $1`, [uuid]);
  return result.rows[0] || null;
}

module.exports = { createJob, listJobs, getJob, updateJob, getFullJobByUuid };
