// src/controllers/galleryController.js
//
// Galería de fotos de instalaciones terminadas, para el sitio público.
// Más simple que kits/productos: no hay "padre" — cada fila es una foto
// suelta con caption opcional. Sube directo por multipart (mismo motivo que
// las demás fotos: el outbox JSON genérico no maneja adjuntos).

const fs = require('fs/promises');
const path = require('path');
const pool = require('../db/pool');
const { GALLERY_PHOTOS_DIR } = require('../config/storage');
const { ALLOWED_MIME_TYPES } = require('../middleware/upload');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
function isValidUuid(v) { return typeof v === 'string' && UUID_RE.test(v); }

function buildFileUrl(uuid) { return `/gallery/${uuid}/file`; }

function toResponse(row) {
  return {
    uuid: row.uuid,
    caption: row.caption,
    active: row.active,
    sort_order: row.sort_order,
    created_at: row.created_at,
    updated_at: row.updated_at,
    url: buildFileUrl(row.uuid),
  };
}

// GET /gallery — incluye inactivas, para la pantalla de gestión.
async function listPhotos(req, res) {
  try {
    const result = await pool.query(
      `SELECT uuid, caption, active, sort_order, created_at, updated_at
       FROM gallery_photos WHERE company_id = $1 AND deleted_at IS NULL
       ORDER BY sort_order ASC, created_at DESC`,
      [req.user.company_id]
    );
    return res.status(200).json({ photos: result.rows.map(toResponse) });
  } catch (err) {
    console.error('[gallery] Error en listPhotos:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al listar la galería.' });
  }
}

// POST /gallery  (multipart, campo "photo")
// otros campos: uuid, caption?, sort_order?, created_by_device_id?
async function uploadPhoto(req, res) {
  const { uuid, caption, sort_order, created_by_device_id } = req.body || {};
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid es requerido y debe ser un UUID válido.' });
  if (!req.file) return res.status(400).json({ error_code: 'missing_file', message: 'Falta el archivo (campo "photo").' });

  try {
    const ext = ALLOWED_MIME_TYPES[req.file.mimetype];
    const dir = path.join(GALLERY_PHOTOS_DIR, String(req.user.company_id));
    const filePath = path.join(dir, `${uuid}.${ext}`);
    const relativePath = path.relative(GALLERY_PHOTOS_DIR, filePath);

    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(filePath, req.file.buffer);

    let insertResult;
    try {
      insertResult = await pool.query(
        `INSERT INTO gallery_photos
           (uuid, company_id, storage_url, caption, sort_order, uploaded_by_user_id, created_by_device_id)
         VALUES ($1, $2, $3, $4, $5, $6, $7)
         RETURNING uuid, caption, active, sort_order, created_at, updated_at`,
        [uuid, req.user.company_id, relativePath, caption || null, sort_order ?? 0, req.user.user_id, created_by_device_id || null]
      );
    } catch (dbErr) {
      await fs.unlink(filePath).catch(() => {});
      throw dbErr;
    }
    return res.status(201).json(toResponse(insertResult.rows[0]));
  } catch (err) {
    if (err.code === '23505') {
      return res.status(409).json({ error_code: 'uuid_conflict', message: 'Ya existe una foto con ese uuid.' });
    }
    console.error('[gallery] Error en uploadPhoto:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al subir la foto.' });
  }
}

// PATCH /gallery/:uuid — editar caption/active/sort_order (no reemplaza el archivo).
const EDITABLE_FIELDS = ['caption', 'active', 'sort_order'];
async function updatePhoto(req, res) {
  const { uuid } = req.params;
  const body = req.body || {};
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });

  const fieldsToUpdate = EDITABLE_FIELDS.filter((f) => Object.prototype.hasOwnProperty.call(body, f));
  if (fieldsToUpdate.length === 0) {
    return res.status(400).json({ error_code: 'no_fields', message: `No se enviaron campos editables (${EDITABLE_FIELDS.join(', ')}).` });
  }
  const setClauses = fieldsToUpdate.map((f, i) => `${f} = $${i + 1}`);
  const values = fieldsToUpdate.map((f) => body[f]);

  try {
    const result = await pool.query(
      `UPDATE gallery_photos
       SET ${setClauses.join(', ')}, updated_at = now()
       WHERE uuid = $${values.length + 1} AND company_id = $${values.length + 2} AND deleted_at IS NULL
       RETURNING uuid, caption, active, sort_order, created_at, updated_at`,
      [...values, uuid, req.user.company_id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error_code: 'not_found', message: 'Foto no encontrada.' });
    return res.status(200).json(toResponse(result.rows[0]));
  } catch (err) {
    console.error('[gallery] Error en updatePhoto:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al actualizar la foto.' });
  }
}

async function removePhoto(req, res) {
  const { uuid } = req.params;
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  try {
    const result = await pool.query(
      `UPDATE gallery_photos SET deleted_at = now(), updated_at = now()
       WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL
       RETURNING uuid`,
      [uuid, req.user.company_id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error_code: 'not_found', message: 'Foto no encontrada.' });
    return res.status(200).json({ ok: true, uuid: result.rows[0].uuid });
  } catch (err) {
    console.error('[gallery] Error en removePhoto:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al eliminar la foto.' });
  }
}

async function servePhotoFile(req, res) {
  const { uuid } = req.params;
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  try {
    const result = await pool.query(
      `SELECT storage_url FROM gallery_photos WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL`,
      [uuid, req.user.company_id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error_code: 'not_found', message: 'Foto no encontrada.' });

    const filePath = path.join(GALLERY_PHOTOS_DIR, result.rows[0].storage_url);
    if (!filePath.startsWith(GALLERY_PHOTOS_DIR)) {
      return res.status(400).json({ error_code: 'invalid_path', message: 'Ruta de archivo inválida.' });
    }
    return res.sendFile(filePath, (err) => {
      if (err && !res.headersSent) {
        console.error('[gallery] Error sirviendo archivo:', err);
        res.status(404).json({ error_code: 'file_missing', message: 'El archivo no se encuentra en el servidor.' });
      }
    });
  } catch (err) {
    console.error('[gallery] Error en servePhotoFile:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al servir la foto.' });
  }
}

module.exports = { listPhotos, uploadPhoto, updatePhoto, removePhoto, servePhotoFile, buildFileUrl };
