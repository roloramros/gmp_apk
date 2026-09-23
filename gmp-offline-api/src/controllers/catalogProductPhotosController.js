// src/controllers/catalogProductPhotosController.js
// Calco de catalogKitPhotosController.js, para catalog_product_photos.

const fs = require('fs/promises');
const path = require('path');
const pool = require('../db/pool');
const { CATALOG_PRODUCT_PHOTOS_DIR } = require('../config/storage');
const { ALLOWED_MIME_TYPES } = require('../middleware/upload');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
function isValidUuid(v) { return typeof v === 'string' && UUID_RE.test(v); }

async function findProduct(companyId, productUuid) {
  const result = await pool.query(
    `SELECT id, uuid FROM catalog_products WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL`,
    [productUuid, companyId]
  );
  return result.rows[0] || null;
}

function buildFileUrl(productUuid, photoUuid) {
  return `/catalog-products/${productUuid}/photos/${photoUuid}/file`;
}

async function getFullProductPhoto(photoUuid) {
  const result = await pool.query(
    `SELECT pp.uuid, pp.sort_order, pp.created_at, pp.updated_at, p.uuid AS product_uuid
     FROM catalog_product_photos pp
     JOIN catalog_products p ON p.id = pp.product_id
     WHERE pp.uuid = $1`,
    [photoUuid]
  );
  const row = result.rows[0];
  if (!row) return null;
  const { product_uuid, ...rest } = row;
  return { ...rest, url: buildFileUrl(product_uuid, row.uuid) };
}

async function uploadPhoto(req, res) {
  const { uuid: productUuid } = req.params;
  const { uuid, sort_order, created_by_device_id } = req.body || {};

  if (!isValidUuid(productUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid de producto inválido en la URL.' });
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid es requerido y debe ser un UUID válido.' });
  if (!req.file) return res.status(400).json({ error_code: 'missing_file', message: 'Falta el archivo (campo "photo").' });

  try {
    const product = await findProduct(req.user.company_id, productUuid);
    if (!product) return res.status(404).json({ error_code: 'not_found', message: 'Producto no encontrado.' });

    const ext = ALLOWED_MIME_TYPES[req.file.mimetype];
    const dir = path.join(CATALOG_PRODUCT_PHOTOS_DIR, String(req.user.company_id), productUuid);
    const filePath = path.join(dir, `${uuid}.${ext}`);
    const relativePath = path.relative(CATALOG_PRODUCT_PHOTOS_DIR, filePath);

    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(filePath, req.file.buffer);

    let insertResult;
    try {
      insertResult = await pool.query(
        `INSERT INTO catalog_product_photos
           (uuid, company_id, product_id, storage_url, sort_order, uploaded_by_user_id, created_by_device_id)
         VALUES ($1, $2, $3, $4, $5, $6, $7)
         RETURNING uuid`,
        [uuid, req.user.company_id, product.id, relativePath, sort_order ?? 0, req.user.user_id, created_by_device_id || null]
      );
    } catch (dbErr) {
      await fs.unlink(filePath).catch(() => {});
      throw dbErr;
    }

    return res.status(201).json(await getFullProductPhoto(insertResult.rows[0].uuid));
  } catch (err) {
    if (err.code === '23505') {
      return res.status(409).json({ error_code: 'uuid_conflict', message: 'Ya existe una foto con ese uuid.' });
    }
    console.error('[catalogProductPhotos] Error en uploadPhoto:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al subir la foto.' });
  }
}

async function removePhoto(req, res) {
  const { uuid: productUuid, photo_uuid: photoUuid } = req.params;
  if (!isValidUuid(productUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid de producto inválido en la URL.' });
  if (!isValidUuid(photoUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'photo_uuid inválido en la URL.' });

  try {
    const product = await findProduct(req.user.company_id, productUuid);
    if (!product) return res.status(404).json({ error_code: 'not_found', message: 'Producto no encontrado.' });

    const result = await pool.query(
      `UPDATE catalog_product_photos SET deleted_at = now(), updated_at = now()
       WHERE uuid = $1 AND product_id = $2 AND company_id = $3 AND deleted_at IS NULL
       RETURNING uuid`,
      [photoUuid, product.id, req.user.company_id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error_code: 'not_found', message: 'Foto no encontrada.' });
    return res.status(200).json({ ok: true, uuid: result.rows[0].uuid });
  } catch (err) {
    console.error('[catalogProductPhotos] Error en removePhoto:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al quitar la foto.' });
  }
}

async function servePhotoFile(req, res) {
  const { uuid: productUuid, photo_uuid: photoUuid } = req.params;
  if (!isValidUuid(productUuid) || !isValidUuid(photoUuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  }
  try {
    const product = await findProduct(req.user.company_id, productUuid);
    if (!product) return res.status(404).json({ error_code: 'not_found', message: 'Producto no encontrado.' });

    const result = await pool.query(
      `SELECT storage_url FROM catalog_product_photos
       WHERE uuid = $1 AND product_id = $2 AND company_id = $3 AND deleted_at IS NULL`,
      [photoUuid, product.id, req.user.company_id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error_code: 'not_found', message: 'Foto no encontrada.' });

    const filePath = path.join(CATALOG_PRODUCT_PHOTOS_DIR, result.rows[0].storage_url);
    if (!filePath.startsWith(CATALOG_PRODUCT_PHOTOS_DIR)) {
      return res.status(400).json({ error_code: 'invalid_path', message: 'Ruta de archivo inválida.' });
    }
    return res.sendFile(filePath, (err) => {
      if (err && !res.headersSent) {
        console.error('[catalogProductPhotos] Error sirviendo archivo:', err);
        res.status(404).json({ error_code: 'file_missing', message: 'El archivo no se encuentra en el servidor.' });
      }
    });
  } catch (err) {
    console.error('[catalogProductPhotos] Error en servePhotoFile:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al servir la foto.' });
  }
}

module.exports = { uploadPhoto, removePhoto, servePhotoFile, getFullProductPhoto, buildFileUrl };
