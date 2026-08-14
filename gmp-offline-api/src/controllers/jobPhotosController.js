// src/controllers/jobPhotosController.js
//
// Fotos de trabajo (multipart, almacenamiento local en el VPS).
// POST y DELETE requieren X-Command-Id (idempotencia).
//
// Roles de ESCRITURA (subir/borrar): admin, o trabajador asignado al job
// (mismo criterio que job_materials — comercial NO puede escribir).
// Roles de LECTURA (servido del archivo): admin/comercial (todo en la
// empresa), trabajador (solo sus jobs asignados), cliente (solo sus jobs) —
// según la tabla de visibilidad de fase1-diseno-datos-sync.md sección 5.
//
// Se bloquea agregar/quitar si el job está 'cancelled' o en cualquier estado
// desde 'invoiced' en adelante (mismo criterio aplicado en job_materials).
//
// Storage: local en disco, bajo JOB_PHOTOS_DIR/<company_id>/<job_uuid>/<photo_uuid>.<ext>.
// El soft delete NO borra el archivo físico (mismo principio que el resto del
// sistema: nunca DELETE real); solo lo hace inaccesible vía la ruta de
// servido, que filtra por deleted_at IS NULL.

const fs = require('fs/promises');
const path = require('path');
const pool = require('../db/pool');
const { JOB_PHOTOS_DIR } = require('../config/storage');
const { ALLOWED_MIME_TYPES } = require('../middleware/upload');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const BLOCKED_STATUSES = ['cancelled', 'invoiced', 'partially_paid', 'paid'];

function isValidUuid(v) {
  return typeof v === 'string' && UUID_RE.test(v);
}

// Job con los campos necesarios para validar estado, pertenencia y visibilidad.
async function findJob(companyId, jobUuid) {
  const result = await pool.query(
    `SELECT id, uuid, status, client_id FROM jobs WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL`,
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

// Visibilidad de LECTURA (más permisiva que la de escritura):
// admin/comercial ven todo; trabajador solo si está asignado; cliente solo si es su job.
async function canViewJob(job, user) {
  if (user.role === 'admin' || user.role === 'comercial') return true;
  if (user.role === 'trabajador') return isAssignedWorker(job.id, user);
  if (user.role === 'cliente') return job.client_id === user.user_id;
  return false;
}

function buildFileUrl(jobUuid, photoUuid) {
  return `/jobs/${jobUuid}/photos/${photoUuid}/file`;
}

async function getFullJobPhoto(photoUuid) {
  const result = await pool.query(
    `SELECT jp.uuid, jp.created_at, jp.updated_at,
            j.uuid AS job_uuid,
            uu.uuid AS uploaded_by_uuid, uu.full_name AS uploaded_by_name
     FROM job_photos jp
     JOIN jobs j ON j.id = jp.job_id
     JOIN users uu ON uu.id = jp.uploaded_by_user_id
     WHERE jp.uuid = $1`,
    [photoUuid]
  );
  const row = result.rows[0];
  if (!row) return null;
  const { job_uuid, ...rest } = row;
  return { ...rest, url: buildFileUrl(job_uuid, row.uuid) };
}

// ---------------------------------------------------------------------------
// POST /jobs/:uuid/photos  (multipart, campo de archivo "photo")
// otros campos del form: uuid, created_by_device_id?
// ---------------------------------------------------------------------------
async function uploadPhoto(req, res) {
  const { uuid: jobUuid } = req.params;
  const { uuid, created_by_device_id } = req.body || {};

  if (!isValidUuid(jobUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid de job inválido en la URL.' });
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid es requerido y debe ser un UUID válido.' });
  if (!req.file) return res.status(400).json({ error_code: 'missing_file', message: 'Falta el archivo (campo "photo").' });

  try {
    const job = await findJob(req.user.company_id, jobUuid);
    if (!job) return res.status(404).json({ error_code: 'not_found', message: 'Job no encontrado.' });
    if (BLOCKED_STATUSES.includes(job.status)) {
      return res.status(409).json({
        error_code: 'job_closed',
        message: `No se pueden agregar fotos a un job en estado '${job.status}'.`,
      });
    }

    const assigned = await isAssignedWorker(job.id, req.user);
    if (!assigned) {
      return res.status(403).json({ error_code: 'forbidden', message: 'No estás asignado a este job.' });
    }

    const ext = ALLOWED_MIME_TYPES[req.file.mimetype];
    const dir = path.join(JOB_PHOTOS_DIR, String(req.user.company_id), jobUuid);
    const filePath = path.join(dir, `${uuid}.${ext}`);
    const relativePath = path.relative(JOB_PHOTOS_DIR, filePath);

    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(filePath, req.file.buffer);

    let insertResult;
    try {
      insertResult = await pool.query(
        `INSERT INTO job_photos
           (uuid, company_id, job_id, storage_url, uploaded_by_user_id, created_by_device_id)
         VALUES ($1, $2, $3, $4, $5, $6)
         RETURNING uuid`,
        [uuid, req.user.company_id, job.id, relativePath, req.user.user_id, created_by_device_id || null]
      );
    } catch (dbErr) {
      // Si falla el INSERT (p. ej. uuid_conflict), no dejamos el archivo huérfano.
      await fs.unlink(filePath).catch(() => {});
      throw dbErr;
    }

    const fullRow = await getFullJobPhoto(insertResult.rows[0].uuid);
    return res.status(201).json(fullRow);
  } catch (err) {
    if (err.code === '23505') {
      return res.status(409).json({ error_code: 'uuid_conflict', message: 'Ya existe una foto con ese uuid.' });
    }
    console.error('[jobPhotos] Error en uploadPhoto:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al subir la foto.' });
  }
}

// ---------------------------------------------------------------------------
// DELETE /jobs/:uuid/photos/:photo_uuid  (soft delete; el archivo físico se conserva)
// ---------------------------------------------------------------------------
async function removePhoto(req, res) {
  const { uuid: jobUuid, photo_uuid: photoUuid } = req.params;

  if (!isValidUuid(jobUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid de job inválido en la URL.' });
  if (!isValidUuid(photoUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'photo_uuid inválido en la URL.' });

  try {
    const job = await findJob(req.user.company_id, jobUuid);
    if (!job) return res.status(404).json({ error_code: 'not_found', message: 'Job no encontrado.' });
    if (BLOCKED_STATUSES.includes(job.status)) {
      return res.status(409).json({
        error_code: 'job_closed',
        message: `No se pueden quitar fotos de un job en estado '${job.status}'.`,
      });
    }

    const assigned = await isAssignedWorker(job.id, req.user);
    if (!assigned) {
      return res.status(403).json({ error_code: 'forbidden', message: 'No estás asignado a este job.' });
    }

    const result = await pool.query(
      `UPDATE job_photos
       SET deleted_at = now(), updated_at = now()
       WHERE uuid = $1 AND job_id = $2 AND company_id = $3 AND deleted_at IS NULL
       RETURNING uuid`,
      [photoUuid, job.id, req.user.company_id]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Foto no encontrada.' });
    }
    return res.status(200).json({ ok: true, uuid: result.rows[0].uuid });
  } catch (err) {
    console.error('[jobPhotos] Error en removePhoto:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al quitar la foto.' });
  }
}

// ---------------------------------------------------------------------------
// GET /jobs/:uuid/photos/:photo_uuid/file
// Ruta de servido autenticada (no es un endpoint de sync ni de comando: no
// lleva X-Command-Id). Valida visibilidad por rol antes de streamear el archivo.
// ---------------------------------------------------------------------------
async function servePhotoFile(req, res) {
  const { uuid: jobUuid, photo_uuid: photoUuid } = req.params;

  if (!isValidUuid(jobUuid) || !isValidUuid(photoUuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  }

  try {
    const job = await findJob(req.user.company_id, jobUuid);
    if (!job) return res.status(404).json({ error_code: 'not_found', message: 'Job no encontrado.' });

    const allowed = await canViewJob(job, req.user);
    if (!allowed) {
      return res.status(403).json({ error_code: 'forbidden', message: 'No tenés acceso a este job.' });
    }

    const result = await pool.query(
      `SELECT storage_url FROM job_photos
       WHERE uuid = $1 AND job_id = $2 AND company_id = $3 AND deleted_at IS NULL`,
      [photoUuid, job.id, req.user.company_id]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Foto no encontrada.' });
    }

    const filePath = path.join(JOB_PHOTOS_DIR, result.rows[0].storage_url);
    // Defensa extra por si storage_url alguna vez viniera corrupto (path traversal).
    if (!filePath.startsWith(JOB_PHOTOS_DIR)) {
      return res.status(400).json({ error_code: 'invalid_path', message: 'Ruta de archivo inválida.' });
    }

    return res.sendFile(filePath, (err) => {
      if (err && !res.headersSent) {
        console.error('[jobPhotos] Error sirviendo archivo:', err);
        res.status(404).json({ error_code: 'file_missing', message: 'El archivo no se encuentra en el servidor.' });
      }
    });
  } catch (err) {
    console.error('[jobPhotos] Error en servePhotoFile:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al servir la foto.' });
  }
}

module.exports = { uploadPhoto, removePhoto, servePhotoFile, getFullJobPhoto, buildFileUrl };
