const jwt = require('jsonwebtoken');

function authenticateSuperAdmin(req, res, next) {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    return res.status(401).json({ error_code: 'no_token', message: 'Falta token de autenticación' });
  }
  const token = header.slice(7);
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET);
    if (payload.type !== 'super_admin') {
      return res.status(403).json({ error_code: 'forbidden', message: 'Token no autorizado para super-admin' });
    }
    req.superAdmin = payload;
    next();
  } catch (err) {
    return res.status(401).json({ error_code: 'invalid_token', message: 'Token inválido o expirado' });
  }
}

module.exports = { authenticateSuperAdmin };
