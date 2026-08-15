// src/controllers/jobMaterialsController.js
//
// Materiales usados en un job (catálogo o texto libre).
// POST, PATCH y DELETE requieren X-Command-Id (idempotencia).
//
// Roles: admin, o trabajador asignado al job (mismo criterio que start/finish).
// Se bloquea agregar/quitar/modificar si el job está 'cancelled' o en cualquier estado
// desde 'invoiced' en adelante (invoiced, partially_paid, paid).

const pool = require('../db/pool');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const BLOCKED_STATUSES = ['cancelled', 'invoiced', 'partially_paid', 'paid'];

function isValidUuid(v) {
  return typeof v === 'string' && UUID_RE.test(v);
}

async function findJob(companyId, jobUuid) {
  const result = await pool.query(
    `SELECT id, status FROM jobs WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL`,
    [jobUuid, companyId]
  );
  return result.rows[0] || null;
}

async function isAssignedWorker(jobId, user) {
  if (user.role !== 'trabajador') return true;
  const result = await pool.query(
    `SELECT 1 FROM job_workers WHERE job_id = $1 AND user_id = $2 AND deleted_at IS NULL`,
    [jobId, user.user_id]
  );
  return result.rows.length > 0;
}

async function resolveMaterial(materialUuid, companyId) {
  const result = await pool.query(
    `SELECT id, default_price FROM materials WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL`,
    [materialUuid, companyId]
  );
  return result.rows[0] || null;
}

async function getFullJobMaterial(jobMaterialUuid) {
  const result = await pool.query(
    `SELECT jm.uuid, jm.quantity, jm.unit_price, jm.free_text_description,
            jm.created_at, jm.updated_at,
            m.uuid AS material_uuid, m.name AS material_name
     FROM job_materials jm
     LEFT JOIN materials m ON m.id = jm.material_id
     WHERE jm.uuid = $1`,
    [jobMaterialUuid]
  );
  return result.rows[0] || null;
}

async function addMaterial(req, res) {
  const { uuid: jobUuid } = req.params;
  const { uuid, material_uuid, free_text_description, quantity, unit_price, created_by_device_id } = req.body || {};

  if (!isValidUuid(jobUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid de job inválido en la URL.' });
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid es requerido y debe ser un UUID válido.' });

  const hasMaterialRef = !!material_uuid;
  const hasFreeText = !!(free_text_description && free_text_description.trim());
  if (hasMaterialRef === hasFreeText) {
    return res.status(400).json({
      error_code: 'invalid_material_reference',
      message: 'Debe enviarse exactamente uno de: material_uuid (catálogo) o free_text_description (texto libre).',
    });
  }
  if (hasMaterialRef && !isValidUuid(material_uuid)) {
    return res.status(400).json({ error_code: 'invalid_material_uuid', message: 'material_uuid debe ser un UUID válido.' });
  }

  const qty = Number(quantity);
  if (!Number.isFinite(qty) || qty <= 0) {
    return res.status(400).json({ error_code: 'invalid_quantity', message: 'quantity debe ser un número mayor a 0.' });
  }

  try {
    const job = await findJob(req.user.company_id, jobUuid);
    if (!job) return res.status(404).json({ error_code: 'not_found', message: 'Job no encontrado.' });
    if (BLOCKED_STATUSES.includes(job.status)) {
      return res.status(409).json({
        error_code: 'job_closed',
        message: `No se pueden agregar materiales a un job en estado '${job.status}'.`,
      });
    }

    const assigned = await isAssignedWorker(job.id, req.user);
    if (!assigned) {
      return res.status(403).json({ error_code: 'forbidden', message: 'No estás asignado a este job.' });
    }

    let materialId = null;
    let resolvedUnitPrice = unit_price ?? null;

    if (hasMaterialRef) {
      const material = await resolveMaterial(material_uuid, req.user.company_id);
      if (!material) {
        return res.status(400).json({ error_code: 'material_not_found', message: 'No se encontró ese material en el catálogo de la empresa.' });
      }
      materialId = material.id;
      if (resolvedUnitPrice === null) resolvedUnitPrice = material.default_price;

      const existing = await pool.query(
        `SELECT uuid FROM job_materials
         WHERE job_id = $1 AND material_id = $2 AND company_id = $3 AND deleted_at IS NULL
         LIMIT 1`,
        [job.id, materialId, req.user.company_id]
      );
      if (existing.rows.length > 0) {
        await pool.query(
          `UPDATE job_materials
           SET quantity = quantity + $1,
               unit_price = COALESCE($2, unit_price),
               updated_at = now()
           WHERE uuid = $3`,
          [qty, resolvedUnitPrice, existing.rows[0].uuid]
        );
        return res.status(200).json(await getFullJobMaterial(existing.rows[0].uuid));
      }
    } else {
      const cleanDescription = free_text_description.trim();
      const existing = await pool.query(
        `SELECT uuid FROM job_materials
         WHERE job_id = $1
           AND material_id IS NULL
           AND free_text_description = $2
           AND company_id = $3
           AND deleted_at IS NULL
         LIMIT 1`,
        [job.id, cleanDescription, req.user.company_id]
      );
      if (existing.rows.length > 0) {
        await pool.query(
          `UPDATE job_materials
           SET quantity = quantity + $1,
               updated_at = now()
           WHERE uuid = $2`,
          [qty, existing.rows[0].uuid]
        );
        return res.status(200).json(await getFullJobMaterial(existing.rows[0].uuid));
      }
    }

    const insertResult = await pool.query(
      `INSERT INTO job_materials
         (uuid, company_id, job_id, material_id, free_text_description, quantity, unit_price, created_by_device_id)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
       RETURNING uuid`,
      [
        uuid,
        req.user.company_id,
        job.id,
        materialId,
        hasFreeText ? free_text_description.trim() : null,
        qty,
        resolvedUnitPrice,
        created_by_device_id || null,
      ]
    );

    return res.status(201).json(await getFullJobMaterial(insertResult.rows[0].uuid));
  } catch (err) {
    if (err.code === '23505') {
      return res.status(409).json({ error_code: 'uuid_conflict', message: 'Ya existe un job_material con ese uuid.' });
    }
    console.error('[jobMaterials] Error en addMaterial:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al agregar el material.' });
  }
}

// Fija la cantidad exacta de una línea existente. Esta operación se usa
// desde administración para corregir cantidades cargadas por cualquier usuario.
async function updateMaterial(req, res) {
  const { uuid: jobUuid, material_uuid: jobMaterialUuid } = req.params;
  const { quantity } = req.body || {};

  if (!isValidUuid(jobUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid de job inválido en la URL.' });
  if (!isValidUuid(jobMaterialUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'material_uuid inválido en la URL.' });

  const qty = Number(quantity);
  if (!Number.isFinite(qty) || qty <= 0) {
    return res.status(400).json({ error_code: 'invalid_quantity', message: 'quantity debe ser un número mayor a 0.' });
  }

  try {
    const job = await findJob(req.user.company_id, jobUuid);
    if (!job) return res.status(404).json({ error_code: 'not_found', message: 'Job no encontrado.' });
    if (BLOCKED_STATUSES.includes(job.status)) {
      return res.status(409).json({
        error_code: 'job_closed',
        message: `No se pueden modificar materiales de un job en estado '${job.status}'.`,
      });
    }

    const result = await pool.query(
      `UPDATE job_materials
       SET quantity = $1, updated_at = now()
       WHERE uuid = $2 AND job_id = $3 AND company_id = $4 AND deleted_at IS NULL
       RETURNING uuid`,
      [qty, jobMaterialUuid, job.id, req.user.company_id]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Material de job no encontrado.' });
    }
    return res.status(200).json(await getFullJobMaterial(result.rows[0].uuid));
  } catch (err) {
    console.error('[jobMaterials] Error en updateMaterial:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al modificar el material.' });
  }
}

async function removeMaterial(req, res) {
  const { uuid: jobUuid, material_uuid: jobMaterialUuid } = req.params;

  if (!isValidUuid(jobUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid de job inválido en la URL.' });
  if (!isValidUuid(jobMaterialUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'material_uuid inválido en la URL.' });

  try {
    const job = await findJob(req.user.company_id, jobUuid);
    if (!job) return res.status(404).json({ error_code: 'not_found', message: 'Job no encontrado.' });
    if (BLOCKED_STATUSES.includes(job.status)) {
      return res.status(409).json({
        error_code: 'job_closed',
        message: `No se pueden quitar materiales de un job en estado '${job.status}'.`,
      });
    }

    const assigned = await isAssignedWorker(job.id, req.user);
    if (!assigned) {
      return res.status(403).json({ error_code: 'forbidden', message: 'No estás asignado a este job.' });
    }

    const result = await pool.query(
      `UPDATE job_materials
       SET deleted_at = now(), updated_at = now()
       WHERE uuid = $1 AND job_id = $2 AND company_id = $3 AND deleted_at IS NULL
       RETURNING uuid`,
      [jobMaterialUuid, job.id, req.user.company_id]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Material de job no encontrado.' });
    }
    return res.status(200).json({ ok: true, uuid: result.rows[0].uuid });
  } catch (err) {
    console.error('[jobMaterials] Error en removeMaterial:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al quitar el material.' });
  }
}

module.exports = { addMaterial, updateMaterial, removeMaterial };
