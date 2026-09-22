// src/routes/catalogKits.js

const express = require('express');
const router = express.Router();

const { authenticate, requireRole } = require('../middleware/auth');
const idempotency = require('../middleware/idempotency');
const { uploadPhotoMiddleware } = require('../middleware/upload');
const catalogKitsController = require('../controllers/catalogKitsController');
const catalogKitPhotosController = require('../controllers/catalogKitPhotosController');

// Lectura y escritura: solo admin. El catálogo es contenido de configuración,
// no algo que comercial/trabajador necesiten tocar (se expone al público
// por su cuenta vía /public/catalog/:slug).
router.get('/', authenticate, requireRole('admin'), catalogKitsController.listKits);
router.get('/:uuid', authenticate, requireRole('admin'), catalogKitsController.getKit);

router.post('/', authenticate, requireRole('admin'), idempotency, catalogKitsController.createKit);
router.patch('/:uuid', authenticate, requireRole('admin'), idempotency, catalogKitsController.updateKit);
router.delete('/:uuid', authenticate, requireRole('admin'), idempotency, catalogKitsController.deleteKit);

router.post(
  '/:uuid/photos',
  authenticate,
  requireRole('admin'),
  uploadPhotoMiddleware,
  idempotency,
  catalogKitPhotosController.uploadPhoto
);
router.delete('/:uuid/photos/:photo_uuid', authenticate, requireRole('admin'), idempotency, catalogKitPhotosController.removePhoto);
router.get('/:uuid/photos/:photo_uuid/file', authenticate, requireRole('admin'), catalogKitPhotosController.servePhotoFile);

module.exports = router;
