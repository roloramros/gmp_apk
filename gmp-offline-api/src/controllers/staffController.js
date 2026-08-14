const bcrypt = require('bcrypt');
const pool = require('../db/pool');

// Crear staff (solo admin)
async function createStaff(req, res) {
  const { phone, password, role, full_name } = req.body;
  if (!phone || !password || !role || !full_name) {
    return res.status(400).json({ error_code: 'missing_fields', message: 'phone, password, role y full_name son requeridos' });
  }
  const validRoles = ['admin', 'comercial', 'trabajador'];
  if (!validRoles.includes(role)) {
    return res.status(400).json({ error_code: 'invalid_role', message: `role debe ser uno de: ${validRoles.join(', ')}` });
  }

  const existing = await pool.query(
    `SELECT id FROM users WHERE company_id = $1 AND phone = $2 AND deleted_at IS NULL`,
    [req.user.company_id, phone]
  );
  if (existing.rows.length > 0) {
    return res.status(409).json({ error_code: 'phone_taken', message: 'Ya existe un usuario con ese teléfono en la empresa' });
  }

  const password_hash = await bcrypt.hash(password, 10);
  const result = await pool.query(
    `INSERT INTO users (company_id, phone, password_hash, role, full_name)
     VALUES ($1, $2, $3, $4, $5)
     RETURNING id, uuid, phone, role, full_name, active, created_at`,
    [req.user.company_id, phone, password_hash, role, full_name]
  );
  res.status(201).json(result.rows[0]);
}

// Listar staff de la empresa
async function listStaff(req, res) {
  const result = await pool.query(
    `SELECT id, uuid, phone, role, full_name, active, created_at, updated_at
     FROM users
     WHERE company_id = $1 AND deleted_at IS NULL
     ORDER BY full_name ASC`,
    [req.user.company_id]
  );
  res.json(result.rows);
}

// Desactivar staff (soft: active = false, no borra)
async function deactivateStaff(req, res) {
  const { uuid } = req.params;
  const result = await pool.query(
    `UPDATE users SET active = false, updated_at = now()
     WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL
     RETURNING id, uuid, full_name, active`,
    [uuid, req.user.company_id]
  );
  if (result.rows.length === 0) {
    return res.status(404).json({ error_code: 'not_found', message: 'Usuario no encontrado' });
  }
  res.json(result.rows[0]);
}

// Informe de trabajos por trabajador y rango de fechas
// Nota: depende de la tabla `jobs` y `job_workers` (Fase 3). Se deja lista la consulta.
async function staffReport(req, res) {
  const { uuid } = req.params;
  const { from, to } = req.query;
  if (!from || !to) {
    return res.status(400).json({ error_code: 'missing_range', message: 'from y to son requeridos (ISO 8601)' });
  }

  const userResult = await pool.query(
    `SELECT id, full_name FROM users WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL`,
    [uuid, req.user.company_id]
  );
  const worker = userResult.rows[0];
  if (!worker) {
    return res.status(404).json({ error_code: 'not_found', message: 'Usuario no encontrado' });
  }

  const jobsResult = await pool.query(
    `SELECT j.id, j.uuid, j.title, j.status, j.started_at, j.finished_at, j.total_amount
     FROM jobs j
     JOIN job_workers jw ON jw.job_id = j.id
     WHERE jw.user_id = $1
       AND jw.deleted_at IS NULL
       AND j.deleted_at IS NULL
       AND j.company_id = $2
       AND j.created_at BETWEEN $3 AND $4
     ORDER BY j.created_at DESC`,
    [worker.id, req.user.company_id, from, to]
  );

  res.json({
    worker: { uuid, full_name: worker.full_name },
    range: { from, to },
    jobs: jobsResult.rows,
    total_jobs: jobsResult.rows.length,
  });
}

module.exports = { createStaff, listStaff, deactivateStaff, staffReport };
