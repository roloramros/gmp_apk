// src/controllers/publicGalleryController.js

const path = require('path');
const pool = require('../db/pool');
const { GALLERY_PHOTOS_DIR } = require('../config/storage');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// GET /public/gallery/:slug
async function getPublicGallery(req, res) {
  const { slug } = req.params;
  if (!slug) return res.status(400).json({ error_code: 'missing_slug', message: 'Falta el slug de la empresa en la URL.' });

  try {
    const companyResult = await pool.query(
      `SELECT uuid, name FROM companies WHERE slug = $1 AND deleted_at IS NULL AND status != 'suspended'`,
      [slug]
    );
    const company = companyResult.rows[0];
    if (!company) return res.status(404).json({ error_code: 'not_found', message: 'Galería no encontrada.' });

    const photosResult = await pool.query(
      `SELECT g.uuid, g.caption,
         format('/public/gallery/%s/photos/%s/file', $1::text, g.uuid) AS url
       FROM gallery_photos g
       JOIN companies c ON c.id = g.company_id
       WHERE c.slug = $1 AND g.active = true AND g.deleted_at IS NULL
       ORDER BY g.sort_order ASC, g.created_at DESC`,
      [slug]
    );

    return res.status(200).json({ company: { name: company.name }, photos: photosResult.rows });
  } catch (err) {
    console.error('[publicGallery] Error en getPublicGallery:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al obtener la galería.' });
  }
}

// GET /public/gallery/:slug/photos/:photo_uuid/file
async function servePublicPhotoFile(req, res) {
  const { slug, photo_uuid: photoUuid } = req.params;
  if (!UUID_RE.test(photoUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'photo_uuid inválido en la URL.' });

  try {
    const result = await pool.query(
      `SELECT g.storage_url
       FROM gallery_photos g
       JOIN companies c ON c.id = g.company_id
       WHERE c.slug = $1 AND g.uuid = $2
         AND g.deleted_at IS NULL AND g.active = true
         AND c.deleted_at IS NULL AND c.status != 'suspended'`,
      [slug, photoUuid]
    );
    if (result.rows.length === 0) return res.status(404).json({ error_code: 'not_found', message: 'Foto no encontrada.' });

    const filePath = path.join(GALLERY_PHOTOS_DIR, result.rows[0].storage_url);
    if (!filePath.startsWith(GALLERY_PHOTOS_DIR)) {
      return res.status(400).json({ error_code: 'invalid_path', message: 'Ruta de archivo inválida.' });
    }
    return res.sendFile(filePath, (err) => {
      if (err && !res.headersSent) {
        console.error('[publicGallery] Error sirviendo archivo:', err);
        res.status(404).json({ error_code: 'file_missing', message: 'El archivo no se encuentra en el servidor.' });
      }
    });
  } catch (err) {
    console.error('[publicGallery] Error en servePublicPhotoFile:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al servir la foto.' });
  }
}

module.exports = { getPublicGallery, servePublicPhotoFile };
