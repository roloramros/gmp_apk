// src/controllers/catalogKitsController.js
//
// CRUD del catálogo de kits/ofertas por empresa (feature "Catálogo").
// Solo admin puede escribir. Alimenta tanto la app (vía /sync) como la
// página pública del catálogo (vía GET /public/catalog/:slug).
//
// POST / PATCH / DELETE pasan por el middleware de idempotencia (X-Command-Id).
// GET es lectura simple, sin idempotencia — incluye kits inactivos (a
// diferencia del endpoint público, que solo muestra active = true).

const pool = require('../db/pool');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function buildFileUrl(kitUuid, photoUuid) {
  return `/catalog-kits/${kitUuid}/photos/${photoUuid}/file`;
}

// SELECT base reutilizado en listar y detalle: incluye fotos anidadas
// (mismo patrón que BASE_SELECT de jobsController.js para workers/materials/photos).
const BASE_SELECT = `
  SELECT
    k.uuid, k.name, k.power_kw, k.voltage, k.battery_kwh, k.panels_count,
    k.price_usd, k.description, k.active, k.sort_order,
    k.created_at, k.updated_at,
    COALESCE(
      (SELECT json_agg(json_build_object(
         'uuid', kp.uuid,
         'url', format('/catalog-kits/%s/photos/%s/file', k.uuid, kp.uuid),
         'sort_order', kp.sort_order
       ) ORDER BY kp.sort_order ASC, kp.created_at ASC)
       FROM catalog_kit_photos kp
       WHERE kp.kit_id = k.id AND kp.deleted_at IS NULL),
      '[]'
    ) AS photos
  FROM catalog_kits k
`;

// POST /catalog-kits
// body: { uuid, name, power_kw?, voltage?, battery_kwh?, panels_count?,
//         price_usd?, description?, active?, sort_order?, created_by_device_id? }
async function createKit(req, res) {
  const {
    uuid, name, power_kw, voltage, battery_kwh, panels_count,
    price_usd, description, active, sort_order, created_by_device_id,
  } = req.body || {};

  if (!uuid || !UUID_RE.test(uuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid es requerido y debe ser un UUID válido.' });
  }
  if (!name || typeof name !== 'string' || !name.trim()) {
    return res.status(400).json({ error_code: 'invalid_name', message: 'name es requerido.' });
  }

  try {
    const result = await pool.query(
      `INSERT INTO catalog_kits
         (uuid, company_id, name, power_kw, voltage, battery_kwh, panels_count,
          price_usd, description, active, sort_order, created_by_device_id)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
       RETURNING uuid`,
      [
        uuid, req.user.company_id, name.trim(), power_kw ?? null, voltage || null,
        battery_kwh ?? null, panels_count ?? null, price_usd ?? null,
        description || null, active === undefined ? true : !!active,
        sort_order ?? 0, created_by_device_id || null,
      ]
    );
    const full = await getFullKit(result.rows[0].uuid);
    return res.status(201).json(full);
  } catch (err) {
    if (err.code === '23505') {
      return res.status(409).json({ error_code: 'uuid_conflict', message: 'Ya existe un kit con ese uuid.' });
    }
    console.error('[catalogKits] Error en createKit:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al crear el kit.' });
  }
}

async function getFullKit(uuid) {
  const result = await pool.query(`${BASE_SELECT} WHERE k.uuid = $1`, [uuid]);
  return result.rows[0] || null;
}

// GET /catalog-kits — incluye inactivos; solo para la pantalla de gestión (admin).
async function listKits(req, res) {
  try {
    const result = await pool.query(
      `${BASE_SELECT}
       WHERE k.company_id = $1 AND k.deleted_at IS NULL
       ORDER BY k.sort_order ASC, k.name ASC`,
      [req.user.company_id]
    );
    return res.status(200).json({ kits: result.rows });
  } catch (err) {
    console.error('[catalogKits] Error en listKits:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al listar los kits.' });
  }
}

// GET /catalog-kits/:uuid
async function getKit(req, res) {
  const { uuid } = req.params;
  if (!UUID_RE.test(uuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  }
  try {
    const result = await pool.query(
      `${BASE_SELECT} WHERE k.uuid = $1 AND k.company_id = $2 AND k.deleted_at IS NULL`,
      [uuid, req.user.company_id]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Kit no encontrado.' });
    }
    return res.status(200).json(result.rows[0]);
  } catch (err) {
    console.error('[catalogKits] Error en getKit:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al obtener el kit.' });
  }
}

// PATCH /catalog-kits/:uuid
const EDITABLE_FIELDS = [
  'name', 'power_kw', 'voltage', 'battery_kwh', 'panels_count',
  'price_usd', 'description', 'active', 'sort_order',
];

async function updateKit(req, res) {
  const { uuid } = req.params;
  const body = req.body || {};

  if (!UUID_RE.test(uuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  }

  const fieldsToUpdate = EDITABLE_FIELDS.filter((f) => Object.prototype.hasOwnProperty.call(body, f));
  if (fieldsToUpdate.length === 0) {
    return res.status(400).json({
      error_code: 'no_fields',
      message: `No se enviaron campos editables (${EDITABLE_FIELDS.join(', ')}).`,
    });
  }

  const setClauses = fieldsToUpdate.map((f, i) => `${f} = $${i + 1}`);
  const values = fieldsToUpdate.map((f) => body[f]);

  try {
    const result = await pool.query(
      `UPDATE catalog_kits
       SET ${setClauses.join(', ')}, updated_at = now()
       WHERE uuid = $${values.length + 1} AND company_id = $${values.length + 2} AND deleted_at IS NULL
       RETURNING uuid`,
      [...values, uuid, req.user.company_id]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Kit no encontrado.' });
    }
    const full = await getFullKit(result.rows[0].uuid);
    return res.status(200).json(full);
  } catch (err) {
    console.error('[catalogKits] Error en updateKit:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al actualizar el kit.' });
  }
}

// DELETE /catalog-kits/:uuid (soft delete; las fotos del kit se sueltan
// también en la misma transacción, para que /sync mande su tombstone y no
// queden huérfanas — a diferencia de un job borrado, acá no se toca el
// archivo físico en disco, total nunca se vuelve a servir tras el soft
// delete del kit padre)
async function deleteKit(req, res) {
  const { uuid } = req.params;
  if (!UUID_RE.test(uuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  }
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    const kitResult = await client.query(
      `SELECT id, uuid FROM catalog_kits WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL FOR UPDATE`,
      [uuid, req.user.company_id]
    );
    if (kitResult.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error_code: 'not_found', message: 'Kit no encontrado.' });
    }
    const kitId = kitResult.rows[0].id;

    await client.query(
      `UPDATE catalog_kit_photos SET deleted_at = now(), updated_at = now()
       WHERE kit_id = $1 AND deleted_at IS NULL`,
      [kitId]
    );
    await client.query(
      `UPDATE catalog_kits SET deleted_at = now(), updated_at = now() WHERE id = $1`,
      [kitId]
    );

    await client.query('COMMIT');
    return res.status(200).json({ ok: true, uuid });
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    console.error('[catalogKits] Error en deleteKit:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al eliminar el kit.' });
  } finally {
    client.release();
  }
}

module.exports = { createKit, listKits, getKit, updateKit, deleteKit, getFullKit, buildFileUrl };
