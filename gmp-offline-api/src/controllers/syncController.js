// src/controllers/syncController.js
//
// GET /sync — pull por cursor, combinado (todas las entidades en un payload),
// según el contrato cerrado en fase1-diseno-datos-sync.md (secciones 3.2 y 5).
//
// - `since` vacío -> full dump filtrado por company_id (y por rol).
// - Paginado a 500 registros por entidad por página, vía `cursor_page` opaco.
//   El cliente sigue pidiendo páginas con el mismo `since` hasta `has_more: false`;
//   solo entonces guarda el `cursor` devuelto como su próximo `since`.
// - El filtrado por rol se aplica en el WHERE de cada query (no post-filtro en
//   memoria), para que la paginación de 500 sea correcta sobre el set ya filtrado.
//
// Nota sobre `job_workers`: la tabla de visibilidad de la Fase 1 (sección 5) no
// lo menciona explícitamente (solo jobs/materials/staff/job_photos+job_materials).
// Se extiende con el mismo criterio que el resto: admin/comercial ven todo;
// trabajador ve las filas de los jobs donde él está asignado (incluye a sus
// compañeros de esos jobs, igual que la regla de "staff"); cliente ve las de
// sus propios jobs. Esto no rompe el contrato general (ver sección 6 de
// fase1-diseno-datos-sync.md).
//
// Nota (Fase 6 — réplica del modelo de la web legada, index.html): se agregaron
// a `jobs` los 11 campos de "montaje" (client_name, client_ci, client_phone,
// latitude, longitude, reference, site_notes, price, payment_method, visit_date,
// proposed_date). visit_date/proposed_date se formatean con to_char a 'YYYY-MM-DD'
// para no arrastrar timezone en columnas DATE (mismo fix aplicado en jobsController.js).
const pool = require('../db/pool');
const PAGE_SIZE = 500;
const EPOCH = '1970-01-01T00:00:00.000Z';
const ENTITY_NAMES = ['jobs', 'job_workers', 'materials', 'job_materials', 'job_photos', 'staff'];
function encodeCursorPage(token) {
  return Buffer.from(JSON.stringify(token), 'utf8').toString('base64');
}
function decodeCursorPage(str) {
  try {
    const decoded = JSON.parse(Buffer.from(str, 'base64').toString('utf8'));
    if (
      decoded &&
      typeof decoded.since === 'string' &&
      typeof decoded.snapshot === 'string' &&
      decoded.offsets &&
      typeof decoded.offsets === 'object'
    ) {
      return decoded;
    }
    return null;
  } catch (err) {
    return null;
  }
}
// Empuja un valor a params y devuelve su placeholder $N, para construir SQL
// dinámico sin desalinear índices (mismo patrón usado en jobsController.js).
function ph(params, value) {
  params.push(value);
  return `$${params.length}`;
}
// Devuelve { rows, hasMore } para una entidad, ya filtrado por company + rol +
// ventana (since, snapshot] + paginado. `rows` siempre incluye `uuid`,
// `updated_at` y `deleted_at` (para poder separar upserts/deletes en el caller),
// más los campos propios de la entidad.
async function queryEntityPage(entityName, user, since, snapshot, offset) {
  const limitPlusOne = PAGE_SIZE + 1;
  let sql;
  const params = [];
  // Cliente no tiene acceso a catálogo de materiales ni a staff (tabla de
  // visibilidad, sección 5): se responde vacío sin consultar la BD.
  if (user.role === 'cliente' && (entityName === 'materials' || entityName === 'staff')) {
    return { rows: [], hasMore: false };
  }
  const pCompany = ph(params, user.company_id);
  const pSince = ph(params, since);
  const pSnapshot = ph(params, snapshot);
  switch (entityName) {
    case 'jobs': {
      let roleFilter = '';
      if (user.role === 'trabajador') {
        const pUser = ph(params, user.user_id);
        roleFilter = `AND EXISTS (
          SELECT 1 FROM job_workers jw2
          WHERE jw2.job_id = j.id AND jw2.user_id = ${pUser} AND
jw2.deleted_at IS NULL
        )`;
      } else if (user.role === 'cliente') {
        const pUser = ph(params, user.user_id);
        roleFilter = `AND j.client_id = ${pUser}`;
      }
      const pLimit = ph(params, limitPlusOne);
      const pOffset = ph(params, offset);
      sql = `
        SELECT j.uuid, j.updated_at, j.deleted_at,
          cu.uuid AS client_uuid, cb.uuid AS created_by_uuid,
          j.title, j.description, j.status, j.address,
          j.scheduled_at, j.started_at, j.finished_at, j.invoiced_at,
          j.total_amount, j.amount_paid, j.cancelled_at, j.created_at,
          j.client_name, j.client_ci, j.client_phone,
          j.latitude, j.longitude, j.reference, j.site_notes,
          j.price, j.payment_method,
          to_char(j.visit_date, 'YYYY-MM-DD') AS visit_date,
          to_char(j.proposed_date, 'YYYY-MM-DD') AS proposed_date
        FROM jobs j
        LEFT JOIN users cu ON cu.id = j.client_id
        JOIN users cb ON cb.id = j.created_by_user_id
        WHERE j.company_id = ${pCompany} AND j.updated_at > ${pSince} AND j.updated_at <= ${pSnapshot}
        ${roleFilter}
        ORDER BY j.updated_at ASC, j.id ASC
        LIMIT ${pLimit} OFFSET ${pOffset}
      `;
      break;
    }
    case 'job_workers': {
      let roleFilter = '';
      if (user.role === 'trabajador') {
        const pUser = ph(params, user.user_id);
        roleFilter = `AND jw.job_id IN (
          SELECT jw3.job_id FROM job_workers jw3
          WHERE jw3.user_id = ${pUser} AND jw3.deleted_at IS NULL        )`;
      } else if (user.role === 'cliente') {
        const pUser = ph(params, user.user_id);
        roleFilter = `AND jw.job_id IN (SELECT id FROM jobs WHERE client_id = ${pUser})`;
      }
      const pLimit = ph(params, limitPlusOne);
      const pOffset = ph(params, offset);
      sql = `
        SELECT jw.uuid, jw.updated_at, jw.deleted_at,
          j.uuid AS job_uuid, u.uuid AS user_uuid, jw.created_at
        FROM job_workers jw
        JOIN jobs j ON j.id = jw.job_id
        JOIN users u ON u.id = jw.user_id
        WHERE jw.company_id = ${pCompany} AND jw.updated_at > ${pSince} AND jw.updated_at <= ${pSnapshot}
        ${roleFilter}
        ORDER BY jw.updated_at ASC, jw.id ASC
        LIMIT ${pLimit} OFFSET ${pOffset}
      `;
      break;
    }
    case 'materials': {
      // admin / comercial / trabajador: todo el catálogo de la empresa (cliente ya se filtró arriba).
      const pLimit = ph(params, limitPlusOne);
      const pOffset = ph(params, offset);
      sql = `
        SELECT m.uuid, m.updated_at, m.deleted_at,
          m.name, m.unit, m.default_price, m.created_at
        FROM materials m
        WHERE m.company_id = ${pCompany} AND m.updated_at > ${pSince} AND m.updated_at <= ${pSnapshot}
        ORDER BY m.updated_at ASC, m.id ASC
        LIMIT ${pLimit} OFFSET ${pOffset}
      `;
      break;
    }
    case 'job_materials': {
      let roleFilter = '';
      if (user.role === 'trabajador') {
        const pUser = ph(params, user.user_id);
        roleFilter = `AND jm.job_id IN (
          SELECT jw2.job_id FROM job_workers jw2
          WHERE jw2.user_id = ${pUser} AND jw2.deleted_at IS NULL        )`;
      } else if (user.role === 'cliente') {
        const pUser = ph(params, user.user_id);
        roleFilter = `AND jm.job_id IN (SELECT id FROM jobs WHERE client_id = ${pUser})`;
      }
      const pLimit = ph(params, limitPlusOne);
      const pOffset = ph(params, offset);
      sql = `
        SELECT jm.uuid, jm.updated_at, jm.deleted_at,
          j.uuid AS job_uuid, m.uuid AS material_uuid,
          jm.free_text_description, jm.quantity, jm.unit_price, jm.created_at
        FROM job_materials jm
        JOIN jobs j ON j.id = jm.job_id
        LEFT JOIN materials m ON m.id = jm.material_id
        WHERE jm.company_id = ${pCompany} AND jm.updated_at > ${pSince} AND jm.updated_at <= ${pSnapshot}
        ${roleFilter}
        ORDER BY jm.updated_at ASC, jm.id ASC
        LIMIT ${pLimit} OFFSET ${pOffset}
      `;
      break;
    }
    case 'job_photos': {
      let roleFilter = '';
      if (user.role === 'trabajador') {
        const pUser = ph(params, user.user_id);
        roleFilter = `AND jp.job_id IN (
          SELECT jw2.job_id FROM job_workers jw2
          WHERE jw2.user_id = ${pUser} AND jw2.deleted_at IS NULL        )`;
      } else if (user.role === 'cliente') {
        const pUser = ph(params, user.user_id);
        roleFilter = `AND jp.job_id IN (SELECT id FROM jobs WHERE client_id = ${pUser})`;
      }
      const pLimit = ph(params, limitPlusOne);
      const pOffset = ph(params, offset);
      sql = `
        SELECT jp.uuid, jp.updated_at, jp.deleted_at,
          j.uuid AS job_uuid, uu.uuid AS uploaded_by_uuid, jp.created_at
        FROM job_photos jp
        JOIN jobs j ON j.id = jp.job_id
        JOIN users uu ON uu.id = jp.uploaded_by_user_id
        WHERE jp.company_id = ${pCompany} AND jp.updated_at > ${pSince} AND jp.updated_at <= ${pSnapshot}
        ${roleFilter}
        ORDER BY jp.updated_at ASC, jp.id ASC
        LIMIT ${pLimit} OFFSET ${pOffset}
      `;
      break;
    }
    case 'staff': {
      // 'staff' = tabla users. cliente ya se filtró arriba (retorno vacío).
      let roleFilter = '';
      if (user.role === 'trabajador') {
        const pUser = ph(params, user.user_id);
        roleFilter = `AND u.id IN (
          SELECT DISTINCT jw2.user_id FROM job_workers jw2
          WHERE jw2.deleted_at IS NULL AND jw2.job_id IN (
            SELECT jw3.job_id FROM job_workers jw3
            WHERE jw3.user_id = ${pUser} AND jw3.deleted_at IS NULL
          )
        )`;
      }
      // admin / comercial: toda la plantilla de la empresa, sin filtro extra.
      const pLimit = ph(params, limitPlusOne);
      const pOffset = ph(params, offset);
      sql = `
        SELECT u.uuid, u.updated_at, u.deleted_at,
          u.phone, u.role, u.full_name, u.active, u.created_at
        FROM users u
        WHERE u.company_id = ${pCompany} AND u.updated_at > ${pSince} AND u.updated_at <= ${pSnapshot}
        ${roleFilter}
        ORDER BY u.updated_at ASC, u.id ASC
        LIMIT ${pLimit} OFFSET ${pOffset}
      `;
      break;
    }
    default:
      return { rows: [], hasMore: false };
  }
  const result = await pool.query(sql, params);
  const hasMore = result.rows.length > PAGE_SIZE;
  const rows = hasMore ? result.rows.slice(0, PAGE_SIZE) : result.rows;
  return { rows, hasMore };
}
// Da forma al payload público de una fila "upsert" según la entidad
// (omite deleted_at/updated_at internos ya usados para paginar, y expone
// solo los campos que el contrato de Fase 1 espera que la app consuma).
function formatUpsert(entityName, row) {
  switch (entityName) {
    case 'jobs':
      return {
        uuid: row.uuid,
        client_uuid: row.client_uuid,
        created_by_uuid: row.created_by_uuid,
        title: row.title,
        description: row.description,
        status: row.status,
        address: row.address,
        scheduled_at: row.scheduled_at,
        started_at: row.started_at,
        finished_at: row.finished_at,
        invoiced_at: row.invoiced_at,
        total_amount: row.total_amount,
        amount_paid: row.amount_paid,
        cancelled_at: row.cancelled_at,
        created_at: row.created_at,
        updated_at: row.updated_at,
        client_name: row.client_name,
        client_ci: row.client_ci,
        client_phone: row.client_phone,
        latitude: row.latitude,
        longitude: row.longitude,
        reference: row.reference,
        site_notes: row.site_notes,
        price: row.price,
        payment_method: row.payment_method,
        visit_date: row.visit_date,
        proposed_date: row.proposed_date,
      };
    case 'job_workers':
      return {
        uuid: row.uuid,
        job_uuid: row.job_uuid,
        user_uuid: row.user_uuid,
        created_at: row.created_at,
        updated_at: row.updated_at,
      };
    case 'materials':
      return {
        uuid: row.uuid,
        name: row.name,
        unit: row.unit,
        default_price: row.default_price,
        created_at: row.created_at,
        updated_at: row.updated_at,
      };
    case 'job_materials':
      return {
        uuid: row.uuid,
        job_uuid: row.job_uuid,
        material_uuid: row.material_uuid,
        free_text_description: row.free_text_description,
        quantity: row.quantity,
        unit_price: row.unit_price,
        created_at: row.created_at,
        updated_at: row.updated_at,
      };
    case 'job_photos':
      return {
        uuid: row.uuid,
        job_uuid: row.job_uuid,
        uploaded_by_uuid: row.uploaded_by_uuid,
        url: `/jobs/${row.job_uuid}/photos/${row.uuid}/file`,
        created_at: row.created_at,
        updated_at: row.updated_at,
      };
    case 'staff':
      return {
        uuid: row.uuid,
        phone: row.phone,
        role: row.role,
        full_name: row.full_name,
        active: row.active,
        created_at: row.created_at,
        updated_at: row.updated_at,
      };
    default:
      return row;
  }
}
// GET /sync?since=<ISO8601|vacío>&cursor_page=<token opaco|vacío>
async function sync(req, res) {
  const user = req.user;
  const { since: sinceRaw, cursor_page: cursorPageRaw } = req.query;
  let since = EPOCH;
  if (sinceRaw !== undefined && sinceRaw !== null && sinceRaw !== '') {
    const parsed = new Date(sinceRaw);
    if (Number.isNaN(parsed.getTime())) {
      return res.status(400).json({
        error_code: 'invalid_since',
        message: 'since debe ser una fecha ISO8601 válida o estar vacío (full dump).',
      });
    }
    since = parsed.toISOString();
  }
  let snapshot;
  let offsets = {};
  ENTITY_NAMES.forEach((e) => {
    offsets[e] = 0;
  });
  if (cursorPageRaw) {
    const decoded = decodeCursorPage(cursorPageRaw);
    if (!decoded) {
      return res.status(400).json({ error_code: 'invalid_cursor_page', message: 'cursor_page inválido.' });
    }
    if (decoded.since !== since) {
      // El cliente debe seguir pidiendo páginas con el mismo `since` hasta
      // agotar la secuencia (has_more: false). Cambiarlo a mitad de camino
      // es un bug de cliente: se falla explícito en vez de servir un set inconsistente.
      return res.status(400).json({
        error_code: 'cursor_page_since_mismatch',
        message: 'cursor_page no corresponde al since indicado en esta llamada.',
      });
    }
    snapshot = decoded.snapshot;
    offsets = { ...offsets, ...decoded.offsets };
  } else {
    // Primera página de la secuencia: fijamos el instante "hasta" (snapshot) usando
    // el reloj de la BD, para que todas las páginas de esta secuencia vean una
    // ventana (since, snapshot] estable aunque lleguen escrituras nuevas mientras se pagina.
    const nowResult = await pool.query('SELECT NOW() AS now');
    snapshot = nowResult.rows[0].now.toISOString();
  }
  try {
    const entities = {};
    const nextOffsets = {};
    let anyHasMore = false;
    for (const entityName of ENTITY_NAMES) {
      const { rows, hasMore } = await queryEntityPage(entityName, user, since, snapshot, offsets[entityName]);
      const upserts = [];
      const deletes = [];
      for (const row of rows) {
        if (row.deleted_at) {
          deletes.push(row.uuid);
        } else {
          upserts.push(formatUpsert(entityName, row));
        }
      }
      entities[entityName] = { upserts, deletes };
      nextOffsets[entityName] = offsets[entityName] + rows.length;
      if (hasMore) anyHasMore = true;
    }
    return res.status(200).json({
      cursor: snapshot,
      has_more: anyHasMore,
      next_cursor_page: anyHasMore ? encodeCursorPage({ since, snapshot, offsets: nextOffsets }) : null,
      entities,
    });
  } catch (err) {
    console.error('[sync] Error en GET /sync:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al sincronizar.' });
  }
}
module.exports = { sync };
