// src/routes/materials.js

const express = require('express');
const router = express.Router();

const { authenticate, requireRole } = require('../middleware/auth');
const idempotency = require('../middleware/idempotency');
const materialsController = require('../controllers/materialsController');

// Lectura: cualquier rol autenticado de la empresa (comercial/trabajador
// también necesitan ver el catálogo para asociar materiales a un job).
router.get('/', authenticate, materialsController.listMaterials);

// Escritura: solo admin. Requiere X-Command-Id (idempotencia) por ser
// un endpoint de acción según el contrato de la Fase 1.
router.post('/', authenticate, requireRole('admin'), idempotency, materialsController.createMaterial);
router.patch('/:uuid', authenticate, requireRole('admin'), idempotency, materialsController.updateMaterial);
router.delete('/:uuid', authenticate, requireRole('admin'), idempotency, materialsController.deleteMaterial);

module.exports = router;
