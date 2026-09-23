// src/routes/gallery.js

const express = require('express');
const router = express.Router();

const { authenticate, requireRole } = require('../middleware/auth');
const idempotency = require('../middleware/idempotency');
const { uploadPhotoMiddleware } = require('../middleware/upload');
const galleryController = require('../controllers/galleryController');

// Solo admin/comercial (decisión: trabajador no gestiona la galería).
router.get('/', authenticate, requireRole('admin', 'comercial'), galleryController.listPhotos);
router.post('/', authenticate, requireRole('admin', 'comercial'), uploadPhotoMiddleware, idempotency, galleryController.uploadPhoto);
router.patch('/:uuid', authenticate, requireRole('admin', 'comercial'), idempotency, galleryController.updatePhoto);
router.delete('/:uuid', authenticate, requireRole('admin', 'comercial'), idempotency, galleryController.removePhoto);
router.get('/:uuid/file', authenticate, requireRole('admin', 'comercial'), galleryController.servePhotoFile);

module.exports = router;
