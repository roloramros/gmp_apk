// src/routes/deviceTokens.js
const express = require('express');
const router = express.Router();
const { authenticate } = require('../middleware/auth');
const { registerDeviceToken, unregisterDeviceToken } = require('../controllers/deviceTokensController');

// Cualquier usuario autenticado (admin/comercial/trabajador/cliente) puede
// registrar el token de su propio dispositivo — no hay restricción de rol.
router.post('/', authenticate, registerDeviceToken);
router.delete('/', authenticate, unregisterDeviceToken);

module.exports = router;
