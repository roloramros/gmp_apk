const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const pool = require('../db/pool');

const TOKEN_EXPIRY = '365d'; // apps móviles: sesiones largas, con logout explícito

async function listCompanies(req, res) {
  const result = await pool.query(
    `SELECT id, uuid, name FROM companies WHERE status != 'suspended' ORDER BY name ASC`
  );
  res.json(result.rows);
}

async function login(req, res) {
  const { company_id, phone, password } = req.body;
  if (!company_id || !phone || !password) {
    return res.status(400).json({ error_code: 'missing_fields', message: 'company_id, phone y password son requeridos' });
  }

  const userResult = await pool.query(
    `SELECT * FROM users WHERE company_id = $1 AND phone = $2 AND deleted_at IS NULL`,
    [company_id, phone]
  );
  const user = userResult.rows[0];

  if (!user || !user.active) {
    return res.status(401).json({ error_code: 'invalid_credentials', message: 'Teléfono o contraseña incorrectos' });
  }

  const match = await bcrypt.compare(password, user.password_hash);
  if (!match) {
    return res.status(401).json({ error_code: 'invalid_credentials', message: 'Teléfono o contraseña incorrectos' });
  }

  const token = jwt.sign(
    { user_id: user.id, uuid: user.uuid, company_id: user.company_id, role: user.role },
    process.env.JWT_SECRET,
    { expiresIn: TOKEN_EXPIRY }
  );

  res.json({
    token,
    user: {
      uuid: user.uuid,
      full_name: user.full_name,
      role: user.role,
      company_id: user.company_id,
    },
  });
}

async function logout(req, res) {
  // Con JWT stateless no hay estado de sesión que invalidar en servidor.
  // El cliente simplemente descarta el token localmente.
  res.json({ status: 'ok' });
}

async function changePassword(req, res) {
  const { old_password, new_password } = req.body;
  if (!old_password || !new_password) {
    return res.status(400).json({ error_code: 'missing_fields', message: 'old_password y new_password son requeridos' });
  }

  const userResult = await pool.query(`SELECT * FROM users WHERE id = $1`, [req.user.user_id]);
  const user = userResult.rows[0];

  const match = await bcrypt.compare(old_password, user.password_hash);
  if (!match) {
    return res.status(401).json({ error_code: 'invalid_credentials', message: 'Contraseña actual incorrecta' });
  }

  const newHash = await bcrypt.hash(new_password, 10);
  await pool.query(
    `UPDATE users SET password_hash = $1, updated_at = now() WHERE id = $2`,
    [newHash, user.id]
  );

  res.json({ status: 'ok' });
}

module.exports = { listCompanies, login, logout, changePassword };
