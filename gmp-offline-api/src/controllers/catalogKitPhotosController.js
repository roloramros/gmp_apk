// src/controllers/catalogKitPhotosController.js
//
// Fotos de kits del catálogo (multipart, storage local en el VPS).
// Mismo patrón que jobPhotosController.js, pero solo admin escribe (no hay
// noción de "trabajador asignado" acá) y el servido autenticado es para la
// pantalla de gestión — la página pública usa su propia ruta sin auth
// (ver publicCatalogController.js), que solo sirve fotos de kits activos.
//
// POST y DELETE requieren X-Command-Id (idempotencia).
// Storage: local en disco, bajo CATALOG_KIT_PHOTOS_DIR/<company_id>/<kit_uuid>/<photo_uuid>.<ext>.
// Soft delete: no borra el archivo físico, solo lo hace inaccesible (deleted_at).

const fs = require('fs/promises');
const path = require('path');
const pool = require('../db/pool');
const { CATALOG_KIT_PHOTOS_DIR } = require('../config/storage');
const { ALLOWED_MIME_TYPES } = require('../middleware/upload');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isValidUuid(v) {
  return typeof v === 'string' && UUID_RE.test(v);
}

async function findKit(companyId, kitUuid) {
  const result = await pool.query(
    `SELECT id, uuid FROM catalog_kits WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL`,
    [kitUuid, companyId]
  );
  return result.rows[0] || null;
}

function buildFileUrl(kitUuid, photoUuid) {
  return `/catalog-kits/${kitUuid}/photos/${photoUuid}/file`;
}

async function getFullKitPhoto(photoUuid) {
  const result = await pool.query(
    `SELECT kp.uuid, kp.sort_order, kp.created_at, kp.updated_at, k.uuid AS kit_uuid
     FROM catalog_kit_photos kp
     JOIN catalog_kits k ON k.id = kp.kit_id
     WHERE kp.uuid = $1`,
    [photoUuid]
  );
  const row = result.rows[0];
  if (!row) return null;
  const { kit_uuid, ...rest } = row;
  return { ...rest, url: buildFileUrl(kit_uuid, row.uuid) };
}

// ---------------------------------------------------------------------------
// POST /catalog-kits/:uuid/photos  (multipart, campo de archivo "photo")
// otros campos del form: uuid, sort_order?, created_by_device_id?
// ---------------------------------------------------------------------------
async function uploadPhoto(req, res) {
  const { uuid: kitUuid } = req.params;
  const { uuid, sort_order, created_by_device_id } = req.body || {};

  if (!isValidUuid(kitUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid de kit inválido en la URL.' });
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid es requerido y debe ser un UUID válido.' });
  if (!req.file) return res.status(400).json({ error_code: 'missing_file', message: 'Falta el archivo (campo "photo").' });

  try {
    const kit = await findKit(req.user.company_id, kitUuid);
    if (!kit) return res.status(404).json({ error_code: 'not_found', message: 'Kit no encontrado.' });

    const ext = ALLOWED_MIME_TYPES[req.file.mimetype];
    const dir = path.join(CATALOG_KIT_PHOTOS_DIR, String(req.user.company_id), kitUuid);
    const filePath = path.join(dir, `${uuid}.${ext}`);
    const relativePath = path.relative(CATALOG_KIT_PHOTOS_DIR, filePath);

    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(filePath, req.file.buffer);

    let insertResult;
    try {
      insertResult = await pool.query(
        `INSERT INTO catalog_kit_photos
           (uuid, company_id, kit_id, storage_url, sort_order, uploaded_by_user_id, created_by_device_id)
         VALUES ($1, $2, $3, $4, $5, $6, $7)
         RETURNING uuid`,
        [uuid, req.user.company_id, kit.id, relativePath, sort_order ?? 0, req.user.user_id, created_by_device_id || null]
      );
    } catch (dbErr) {
      await fs.unlink(filePath).catch(() => {});
      throw dbErr;
    }

    const fullRow = await getFullKitPhoto(insertResult.rows[0].uuid);
    return res.status(201).json(fullRow);
  } catch (err) {
    if (err.code === '23505') {
      return res.status(409).json({ error_code: 'uuid_conflict', message: 'Ya existe una foto con ese uuid.' });
    }
    console.error('[catalogKitPhotos] Error en uploadPhoto:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al subir la foto.' });
  }
}

// ---------------------------------------------------------------------------
// DELETE /catalog-kits/:uuid/photos/:photo_uuid
// ---------------------------------------------------------------------------
async function removePhoto(req, res) {
  const { uuid: kitUuid, photo_uuid: photoUuid } = req.params;

  if (!isValidUuid(kitUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid de kit inválido en la URL.' });
  if (!isValidUuid(photoUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'photo_uuid inválido en la URL.' });

  try {
    const kit = await findKit(req.user.company_id, kitUuid);
    if (!kit) return res.status(404).json({ error_code: 'not_found', message: 'Kit no encontrado.' });

    const result = await pool.query(
      `UPDATE catalog_kit_photos
       SET deleted_at = now(), updated_at = now()
       WHERE uuid = $1 AND kit_id = $2 AND company_id = $3 AND deleted_at IS NULL
       RETURNING uuid`,
      [photoUuid, kit.id, req.user.company_id]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Foto no encontrada.' });
    }
    return res.status(200).json({ ok: true, uuid: result.rows[0].uuid });
  } catch (err) {
    console.error('[catalogKitPhotos] Error en removePhoto:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al quitar la foto.' });
  }
}

// ---------------------------------------------------------------------------
// GET /catalog-kits/:uuid/photos/:photo_uuid/file
// Servido autenticado (para la pantalla de gestión, no la página pública).
// ---------------------------------------------------------------------------
async function servePhotoFile(req, res) {
  const { uuid: kitUuid, photo_uuid: photoUuid } = req.params;

  if (!isValidUuid(kitUuid) || !isValidUuid(photoUuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  }

  try {
    const kit = await findKit(req.user.company_id, kitUuid);
    if (!kit) return res.status(404).json({ error_code: 'not_found', message: 'Kit no encontrado.' });

    const result = await pool.query(
      `SELECT storage_url FROM catalog_kit_photos
       WHERE uuid = $1 AND kit_id = $2 AND company_id = $3 AND deleted_at IS NULL`,
      [photoUuid, kit.id, req.user.company_id]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Foto no encontrada.' });
    }

    const filePath = path.join(CATALOG_KIT_PHOTOS_DIR, result.rows[0].storage_url);
    if (!filePath.startsWith(CATALOG_KIT_PHOTOS_DIR)) {
      return res.status(400).json({ error_code: 'invalid_path', message: 'Ruta de archivo inválida.' });
    }

    return res.sendFile(filePath, (err) => {
      if (err && !res.headersSent) {
        console.error('[catalogKitPhotos] Error sirviendo archivo:', err);
        res.status(404).json({ error_code: 'file_missing', message: 'El archivo no se encuentra en el servidor.' });
      }
    });
  } catch (err) {
    console.error('[catalogKitPhotos] Error en servePhotoFile:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al servir la foto.' });
  }
}

module.exports = { uploadPhoto, removePhoto, servePhotoFile, getFullKitPhoto, buildFileUrl };
