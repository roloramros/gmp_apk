// src/routes/catalogProducts.js

const express = require('express');
const router = express.Router();

const { authenticate, requireRole } = require('../middleware/auth');
const idempotency = require('../middleware/idempotency');
const { uploadPhotoMiddleware } = require('../middleware/upload');
const catalogProductsController = require('../controllers/catalogProductsController');
const catalogProductPhotosController = require('../controllers/catalogProductPhotosController');

router.get('/', authenticate, requireRole('admin', 'comercial'), catalogProductsController.listProducts);
router.get('/:uuid', authenticate, requireRole('admin', 'comercial'), catalogProductsController.getProduct);

router.post('/', authenticate, requireRole('admin', 'comercial'), idempotency, catalogProductsController.createProduct);
router.patch('/:uuid', authenticate, requireRole('admin', 'comercial'), idempotency, catalogProductsController.updateProduct);
router.delete('/:uuid', authenticate, requireRole('admin', 'comercial'), idempotency, catalogProductsController.deleteProduct);

router.post(
  '/:uuid/photos',
  authenticate,
  requireRole('admin', 'comercial'),
  uploadPhotoMiddleware,
  idempotency,
  catalogProductPhotosController.uploadPhoto
);
router.delete('/:uuid/photos/:photo_uuid', authenticate, requireRole('admin', 'comercial'), idempotency, catalogProductPhotosController.removePhoto);
router.get('/:uuid/photos/:photo_uuid/file', authenticate, requireRole('admin', 'comercial'), catalogProductPhotosController.servePhotoFile);

module.exports = router;
