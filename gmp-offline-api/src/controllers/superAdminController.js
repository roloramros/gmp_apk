const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const pool = require('../db/pool');

const TOKEN_EXPIRY = '30d';

async function login(req, res) {
  const { email, password } = req.body;
  if (!email || !password) {
    return res.status(400).json({ error_code: 'missing_fields', message: 'email y password son requeridos' });
  }
  const result = await pool.query(`SELECT * FROM super_admins WHERE email = $1`, [email]);
  const admin = result.rows[0];
  if (!admin) {
    return res.status(401).json({ error_code: 'invalid_credentials', message: 'Credenciales incorrectas' });
  }
  const match = await bcrypt.compare(password, admin.password_hash);
  if (!match) {
    return res.status(401).json({ error_code: 'invalid_credentials', message: 'Credenciales incorrectas' });
  }
  const token = jwt.sign(
    { type: 'super_admin', admin_id: admin.id, uuid: admin.uuid },
    process.env.JWT_SECRET,
    { expiresIn: TOKEN_EXPIRY }
  );
  res.json({ token, admin: { uuid: admin.uuid, email: admin.email, full_name: admin.full_name } });
}

async function createCompany(req, res) {
  const { name } = req.body;
  if (!name) {
    return res.status(400).json({ error_code: 'missing_fields', message: 'name es requerido' });
  }
  const result = await pool.query(
    `INSERT INTO companies (name, status) VALUES ($1, 'trial') RETURNING id, uuid, name, status, created_at`,
    [name]
  );
  res.status(201).json(result.rows[0]);
}

async function createCompanyAdmin(req, res) {
  const { company_id } = req.params;
  const { phone, password, full_name } = req.body;
  if (!phone || !password || !full_name) {
    return res.status(400).json({ error_code: 'missing_fields', message: 'phone, password y full_name son requeridos' });
  }
  const companyResult = await pool.query(
    `SELECT id FROM companies WHERE id = $1 AND deleted_at IS NULL`,
    [company_id]
  );
  if (companyResult.rows.length === 0) {
    return res.status(404).json({ error_code: 'not_found', message: 'Empresa no encontrada' });
  }
  const password_hash = await bcrypt.hash(password, 10);
  const result = await pool.query(
    `INSERT INTO users (company_id, phone, password_hash, role, full_name)
     VALUES ($1, $2, $3, 'admin', $4)
     RETURNING id, uuid, phone, role, full_name, created_at`,
    [company_id, phone, password_hash, full_name]
  );
  res.status(201).json(result.rows[0]);
}

async function listCompanies(req, res) {
  const result = await pool.query(
    `SELECT id, uuid, name, status, created_at, updated_at
     FROM companies WHERE deleted_at IS NULL ORDER BY created_at DESC`
  );
  res.json(result.rows);
}

async function updateCompanyStatus(req, res) {
  const { company_id } = req.params;
  const { status } = req.body;
  const validStatuses = ['trial', 'active', 'suspended'];
  if (!validStatuses.includes(status)) {
    return res.status(400).json({ error_code: 'invalid_status', message: `status debe ser uno de: ${validStatuses.join(', ')}` });
  }
  const result = await pool.query(
    `UPDATE companies SET status = $1, updated_at = now()
     WHERE id = $2 AND deleted_at IS NULL
     RETURNING id, uuid, name, status`,
    [status, company_id]
  );
  if (result.rows.length === 0) {
    return res.status(404).json({ error_code: 'not_found', message: 'Empresa no encontrada' });
  }
  res.json(result.rows[0]);
}

async function deleteCompany(req, res) {
  const { company_id } = req.params;
  const result = await pool.query(
    `UPDATE companies SET deleted_at = now(), updated_at = now()
     WHERE id = $1 AND deleted_at IS NULL
     RETURNING id, uuid, name`,
    [company_id]
  );
  if (result.rows.length === 0) {
    return res.status(404).json({ error_code: 'not_found', message: 'Empresa no encontrada' });
  }
  res.json({ status: 'ok', deleted: result.rows[0] });
}

async function generateBillingReport(req, res) {
  const { company_id, period_from, period_to } = req.body;
  if (!company_id || !period_from || !period_to) {
    return res.status(400).json({ error_code: 'missing_fields', message: 'company_id, period_from y period_to son requeridos' });
  }
  // Placeholder de agregación real: se completa en Fase 3 cuando exista `jobs` con pagos reales.
  const jobsResult = await pool.query(
    `SELECT COUNT(*) AS total_jobs, COALESCE(SUM(amount_paid), 0) AS total_paid
     FROM jobs
     WHERE company_id = $1 AND deleted_at IS NULL AND created_at BETWEEN $2 AND $3`,
    [company_id, period_from, period_to]
  );
  const reportData = jobsResult.rows[0];
  const result = await pool.query(
    `INSERT INTO billing_reports (company_id, period_from, period_to, generated_by_super_admin_id, report_data)
     VALUES ($1, $2, $3, $4, $5)
     RETURNING id, uuid, company_id, period_from, period_to, report_data, created_at`,
    [company_id, period_from, period_to, req.superAdmin.admin_id, reportData]
  );
  res.status(201).json(result.rows[0]);
}

async function listBillingReports(req, res) {
  const result = await pool.query(
    `SELECT id, uuid, company_id, period_from, period_to, report_data, created_at
     FROM billing_reports ORDER BY created_at DESC`
  );
  res.json(result.rows);
}

module.exports = {
  login, createCompany, createCompanyAdmin, listCompanies,
  updateCompanyStatus, deleteCompany, generateBillingReport, listBillingReports,
};
