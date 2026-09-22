// src/controllers/companyProfileController.js
//
// Perfil público de la empresa (feature "Catálogo"): datos que se muestran
// en la página pública del catálogo (GET /public/catalog/:slug).
//
// El `slug` (URL del catálogo, ej. cod-ram.click/catalogos/ricali) se crea
// manualmente por super-admin al dar de alta la empresa (ver
// superAdminController.js) — acá es solo lectura. Lo que el admin de la
// empresa SÍ puede editar él mismo son sus datos de contacto público.

const pool = require('../db/pool');

// GET /company/profile
async function getProfile(req, res) {
  try {
    const result = await pool.query(
      `SELECT uuid, name, slug, contact_whatsapp, contact_facebook, contact_instagram, updated_at
       FROM companies WHERE id = $1 AND deleted_at IS NULL`,
      [req.user.company_id]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Empresa no encontrada.' });
    }
    return res.status(200).json(result.rows[0]);
  } catch (err) {
    console.error('[companyProfile] Error en getProfile:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al obtener el perfil.' });
  }
}

// PATCH /company/profile
// body: campos editables { contact_whatsapp?, contact_facebook?, contact_instagram? }
// (slug y name quedan fuera a propósito — no son autoservicio del admin)
const EDITABLE_FIELDS = ['contact_whatsapp', 'contact_facebook', 'contact_instagram'];

async function updateProfile(req, res) {
  const body = req.body || {};
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
      `UPDATE companies
       SET ${setClauses.join(', ')}, updated_at = now()
       WHERE id = $${values.length + 1} AND deleted_at IS NULL
       RETURNING uuid, name, slug, contact_whatsapp, contact_facebook, contact_instagram, updated_at`,
      [...values, req.user.company_id]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Empresa no encontrada.' });
    }
    return res.status(200).json(result.rows[0]);
  } catch (err) {
    console.error('[companyProfile] Error en updateProfile:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al actualizar el perfil.' });
  }
}

module.exports = { getProfile, updateProfile };
