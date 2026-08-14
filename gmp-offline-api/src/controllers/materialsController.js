// src/controllers/materialsController.js
//
// CRUD del catálogo de materiales por empresa.
// POST / PATCH / DELETE pasan por el middleware de idempotencia (X-Command-Id).
// GET es lectura simple, sin idempotencia.

const pool = require('../db/pool');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// POST /materials
// body: { uuid, name, unit?, default_price?, created_by_device_id? }
async function createMaterial(req, res) {
  const { uuid, name, unit, default_price, created_by_device_id } = req.body || {};

  if (!uuid || !UUID_RE.test(uuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid es requerido y debe ser un UUID válido.' });
  }
  if (!name || typeof name !== 'string' || !name.trim()) {
    return res.status(400).json({ error_code: 'invalid_name', message: 'name es requerido.' });
  }

  try {
    const result = await pool.query(
      `INSERT INTO materials (uuid, company_id, name, unit, default_price, created_by_device_id)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING uuid, name, unit, default_price, created_at, updated_at`,
      [uuid, req.user.company_id, name.trim(), unit || null, default_price ?? null, created_by_device_id || null]
    );
    return res.status(201).json(result.rows[0]);
  } catch (err) {
    if (err.code === '23505') {
      // unique_violation sobre uuid: el cliente reintentó con el mismo uuid de recurso
      // pero con un command_uuid distinto (caso borde, ya que command_log normalmente
      // lo evita). Se responde explícito en vez de fallar oscuro.
      return res.status(409).json({ error_code: 'uuid_conflict', message: 'Ya existe un material con ese uuid.' });
    }
    console.error('[materials] Error en createMaterial:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al crear el material.' });
  }
}

// GET /materials
async function listMaterials(req, res) {
  try {
    const result = await pool.query(
      `SELECT uuid, name, unit, default_price, created_at, updated_at
       FROM materials
       WHERE company_id = $1 AND deleted_at IS NULL
       ORDER BY name ASC`,
      [req.user.company_id]
    );
    return res.status(200).json({ materials: result.rows });
  } catch (err) {
    console.error('[materials] Error en listMaterials:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al listar materiales.' });
  }
}

// PATCH /materials/:uuid
// body: campos editables { name?, unit?, default_price? }
const EDITABLE_FIELDS = ['name', 'unit', 'default_price'];

async function updateMaterial(req, res) {
  const { uuid } = req.params;
  const body = req.body || {};

  if (!UUID_RE.test(uuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  }

  const fieldsToUpdate = EDITABLE_FIELDS.filter((f) => Object.prototype.hasOwnProperty.call(body, f));
  if (fieldsToUpdate.length === 0) {
    return res.status(400).json({ error_code: 'no_fields', message: 'No se enviaron campos editables (name, unit, default_price).' });
  }

  const setClauses = fieldsToUpdate.map((f, i) => `${f} = $${i + 1}`);
  const values = fieldsToUpdate.map((f) => body[f]);

  try {
    const result = await pool.query(
      `UPDATE materials
       SET ${setClauses.join(', ')}, updated_at = now()
       WHERE uuid = $${values.length + 1} AND company_id = $${values.length + 2} AND deleted_at IS NULL
       RETURNING uuid, name, unit, default_price, created_at, updated_at`,
      [...values, uuid, req.user.company_id]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Material no encontrado.' });
    }
    return res.status(200).json(result.rows[0]);
  } catch (err) {
    console.error('[materials] Error en updateMaterial:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al actualizar el material.' });
  }
}

// DELETE /materials/:uuid  (soft delete)
async function deleteMaterial(req, res) {
  const { uuid } = req.params;

  if (!UUID_RE.test(uuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  }

  try {
    const result = await pool.query(
      `UPDATE materials
       SET deleted_at = now(), updated_at = now()
       WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL
       RETURNING uuid`,
      [uuid, req.user.company_id]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Material no encontrado.' });
    }
    return res.status(200).json({ ok: true, uuid: result.rows[0].uuid });
  } catch (err) {
    console.error('[materials] Error en deleteMaterial:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al eliminar el material.' });
  }
}

module.exports = { createMaterial, listMaterials, updateMaterial, deleteMaterial };
