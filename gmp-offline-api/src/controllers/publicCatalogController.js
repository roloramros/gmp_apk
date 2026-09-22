// src/controllers/publicCatalogController.js
//
// Endpoint público (sin autenticación) que consume la página estática del
// catálogo, bajo cod-ram.click/catalogos/:slug. Solo expone: datos públicos
// de la empresa (nombre, contacto) y sus kits ACTIVOS (active = true), con
// sus fotos. Nunca expone nada que no sea explícitamente público — no hay
// aquí acceso a jobs, staff, materiales de trabajo, etc.

const path = require('path');
const pool = require('../db/pool');
const { CATALOG_KIT_PHOTOS_DIR } = require('../config/storage');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// GET /public/catalog/:slug
async function getPublicCatalog(req, res) {
  const { slug } = req.params;
  if (!slug) {
    return res.status(400).json({ error_code: 'missing_slug', message: 'Falta el slug de la empresa en la URL.' });
  }

  try {
    const companyResult = await pool.query(
      `SELECT uuid, name, contact_whatsapp, contact_facebook, contact_instagram
       FROM companies
       WHERE slug = $1 AND deleted_at IS NULL AND status != 'suspended'`,
      [slug]
    );
    const company = companyResult.rows[0];
    if (!company) {
      return res.status(404).json({ error_code: 'not_found', message: 'Catálogo no encontrado.' });
    }

    const kitsResult = await pool.query(
      `SELECT
         k.uuid, k.name, k.power_kw, k.voltage, k.battery_kwh, k.panels_count,
         k.price_usd, k.description,
         COALESCE(
           (SELECT json_agg(json_build_object(
              'url', format('/public/catalog/%s/photos/%s/file', $1::text, kp.uuid)
            ) ORDER BY kp.sort_order ASC, kp.created_at ASC)
            FROM catalog_kit_photos kp
            WHERE kp.kit_id = k.id AND kp.deleted_at IS NULL),
           '[]'
         ) AS photos
       FROM catalog_kits k
       JOIN companies c ON c.id = k.company_id
       WHERE c.slug = $1 AND k.active = true AND k.deleted_at IS NULL
       ORDER BY k.sort_order ASC, k.name ASC`,
      [slug]
    );

    return res.status(200).json({
      company: {
        name: company.name,
        contact_whatsapp: company.contact_whatsapp,
        contact_facebook: company.contact_facebook,
        contact_instagram: company.contact_instagram,
      },
      kits: kitsResult.rows,
    });
  } catch (err) {
    console.error('[publicCatalog] Error en getPublicCatalog:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al obtener el catálogo.' });
  }
}

// GET /public/catalog/:slug/photos/:photo_uuid/file
// Sirve solo fotos de kits ACTIVOS de una empresa no suspendida/no borrada —
// una foto de un kit desactivado o de una empresa suspendida deja de resolver.
async function servePublicPhotoFile(req, res) {
  const { slug, photo_uuid: photoUuid } = req.params;

  if (!UUID_RE.test(photoUuid)) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'photo_uuid inválido en la URL.' });
  }

  try {
    const result = await pool.query(
      `SELECT kp.storage_url
       FROM catalog_kit_photos kp
       JOIN catalog_kits k ON k.id = kp.kit_id
       JOIN companies c ON c.id = k.company_id
       WHERE c.slug = $1 AND kp.uuid = $2
         AND kp.deleted_at IS NULL AND k.deleted_at IS NULL AND k.active = true
         AND c.deleted_at IS NULL AND c.status != 'suspended'`,
      [slug, photoUuid]
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
        console.error('[publicCatalog] Error sirviendo archivo:', err);
        res.status(404).json({ error_code: 'file_missing', message: 'El archivo no se encuentra en el servidor.' });
      }
    });
  } catch (err) {
    console.error('[publicCatalog] Error en servePublicPhotoFile:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al servir la foto.' });
  }
}

module.exports = { getPublicCatalog, servePublicPhotoFile };
