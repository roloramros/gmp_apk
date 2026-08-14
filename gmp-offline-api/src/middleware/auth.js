const jwt = require('jsonwebtoken');

function authenticate(req, res, next) {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    return res.status(401).json({ error_code: 'no_token', message: 'Falta token de autenticación' });
  }
  const token = header.slice(7);
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET);
    req.user = payload; // { user_id, uuid, company_id, role }
    next();
  } catch (err) {
    return res.status(401).json({ error_code: 'invalid_token', message: 'Token inválido o expirado' });
  }
}

function requireRole(...roles) {
  return (req, res, next) => {
    if (!req.user || !roles.includes(req.user.role)) {
      return res.status(403).json({ error_code: 'forbidden', message: 'No tienes permiso para esta acción' });
    }
    next();
  };
}

module.exports = { authenticate, requireRole };
