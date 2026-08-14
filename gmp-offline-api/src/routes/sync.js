// src/routes/sync.js

const express = require('express');
const router = express.Router();

const { authenticate } = require('../middleware/auth');
const syncController = require('../controllers/syncController');

// Lectura: cualquier rol autenticado. La visibilidad por rol se filtra dentro
// del controller (sección 5 de fase1-diseno-datos-sync.md), no a nivel de ruta.
// No lleva idempotency: es un GET, no un comando de escritura.
router.get('/', authenticate, syncController.sync);

module.exports = router;
