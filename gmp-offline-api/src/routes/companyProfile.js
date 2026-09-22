// src/routes/companyProfile.js

const express = require('express');
const router = express.Router();

const { authenticate, requireRole } = require('../middleware/auth');
const companyProfileController = require('../controllers/companyProfileController');

// Edición de datos de contacto público (WhatsApp/Facebook/Instagram).
// No lleva X-Command-Id: se asume edición online desde una pantalla de
// configuración, no una acción que deba quedar en el outbox offline.
router.get('/company/profile', authenticate, requireRole('admin'), companyProfileController.getProfile);
router.patch('/company/profile', authenticate, requireRole('admin'), companyProfileController.updateProfile);

module.exports = router;
