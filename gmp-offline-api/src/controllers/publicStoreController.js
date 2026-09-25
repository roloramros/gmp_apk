// src/controllers/publicStoreController.js
// Mismo criterio que publicCatalogController.js pero para catalog_products.

const path = require('path');
const pool = require('../db/pool');
const { CATALOG_PRODUCT_PHOTOS_DIR } = require('../config/storage');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// GET /public/store/:slug
// Devuelve la lista plana de productos activos.
async function getPublicStore(req, res) {
  const { slug } = req.params;
  if (!slug) return res.status(400).json({ error_code: 'missing_slug', message: 'Falta el slug de la empresa en la URL.' });

  try {
    const companyResult = await pool.query(
      `SELECT uuid, name, contact_whatsapp, contact_facebook, contact_instagram
       FROM companies WHERE slug = $1 AND deleted_at IS NULL AND status != 'suspended'`,
      [slug]
    );
    const company = companyResult.rows[0];
    if (!company) return res.status(404).json({ error_code: 'not_found', message: 'Tienda no encontrada.' });

    const productsResult = await pool.query(
      `SELECT
         p.uuid, p.name, p.price_usd, p.description, p.in_stock,
         COALESCE(
           (SELECT json_agg(json_build_object(
              'url', format('/public/store/%s/photos/%s/file', $1::text, pp.uuid)
            ) ORDER BY pp.sort_order ASC, pp.created_at ASC)
            FROM catalog_product_photos pp
            WHERE pp.product_id = p.id AND pp.deleted_at IS NULL),
           '[]'
         ) AS photos
       FROM catalog_products p
       JOIN companies c ON c.id = p.company_id
       WHERE c.slug = $1 AND p.active = true AND p.deleted_at IS NULL
       ORDER BY p.sort_order ASC, p.name ASC`,
      [slug]
    );

    return res.status(200).json({
      company: {
        name: company.name,
        contact_whatsapp: company.contact_whatsapp,
        contact_facebook: company.contact_facebook,
        contact_instagram: company.contact_instagram,
      },
      products: productsResult.rows,
    });
  } catch (err) {
    console.error('[publicStore] Error en getPublicStore:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al obtener la tienda.' });
  }
}

// GET /public/store/:slug/photos/:photo_uuid/file
async function servePublicPhotoFile(req, res) {
  const { slug, photo_uuid: photoUuid } = req.params;
  if (!UUID_RE.test(photoUuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'photo_uuid inválido en la URL.' });

  try {
    const result = await pool.query(
      `SELECT pp.storage_url
       FROM catalog_product_photos pp
       JOIN catalog_products p ON p.id = pp.product_id
       JOIN companies c ON c.id = p.company_id
       WHERE c.slug = $1 AND pp.uuid = $2
         AND pp.deleted_at IS NULL AND p.deleted_at IS NULL AND p.active = true
         AND c.deleted_at IS NULL AND c.status != 'suspended'`,
      [slug, photoUuid]
    );
    if (result.rows.length === 0) return res.status(404).json({ error_code: 'not_found', message: 'Foto no encontrada.' });

    const filePath = path.join(CATALOG_PRODUCT_PHOTOS_DIR, result.rows[0].storage_url);
    if (!filePath.startsWith(CATALOG_PRODUCT_PHOTOS_DIR)) {
      return res.status(400).json({ error_code: 'invalid_path', message: 'Ruta de archivo inválida.' });
    }
    return res.sendFile(filePath, (err) => {
      if (err && !res.headersSent) {
        console.error('[publicStore] Error sirviendo archivo:', err);
        res.status(404).json({ error_code: 'file_missing', message: 'El archivo no se encuentra en el servidor.' });
      }
    });
  } catch (err) {
    console.error('[publicStore] Error en servePublicPhotoFile:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al servir la foto.' });
  }
}

module.exports = { getPublicStore, servePublicPhotoFile };
