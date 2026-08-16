// src/routes/jobs.js

const express = require('express');
const router = express.Router();

const { authenticate, requireRole } = require('../middleware/auth');
const idempotency = require('../middleware/idempotency');
const { uploadPhotoMiddleware } = require('../middleware/upload');
const workerPhotoLimit = require('../middleware/workerPhotoLimit');
const jobsController = require('../controllers/jobsController');
const jobsActionsController = require('../controllers/jobsActionsController');
const jobRegularizeController = require('../controllers/jobRegularizeController');
const jobDeleteController = require('../controllers/jobDeleteController');
const jobMaterialsController = require('../controllers/jobMaterialsController');
const jobPhotosController = require('../controllers/jobPhotosController');

// Lectura: cualquier rol autenticado; la visibilidad por rol se filtra
// dentro del controller (admin/comercial ven todo, trabajador/cliente solo lo suyo).
router.get('/', authenticate, jobsController.listJobs);
router.get('/:uuid', authenticate, jobsController.getJob);

// Escritura: solo admin y comercial. Requiere X-Command-Id (idempotencia).
router.post('/', authenticate, requireRole('admin', 'comercial'), idempotency, jobsController.createJob);
router.patch('/:uuid', authenticate, requireRole('admin', 'comercial'), idempotency, jobsController.updateJob);
router.delete('/:uuid', authenticate, requireRole('admin', 'comercial'), idempotency, jobDeleteController.deleteJob);

// Acciones de estado (outbox de comandos).
router.post('/:uuid/assign', authenticate, requireRole('admin'), idempotency, jobsActionsController.assignWorker);
router.post('/:uuid/unassign', authenticate, requireRole('admin'), idempotency, jobsActionsController.unassignWorker);
router.post('/:uuid/start', authenticate, requireRole('admin', 'trabajador'), idempotency, jobsActionsController.startJob);
router.post('/:uuid/finish', authenticate, requireRole('admin', 'trabajador'), idempotency, jobsActionsController.finishJob);
router.post('/:uuid/invoice', authenticate, requireRole('admin'), idempotency, jobsActionsController.invoiceJob);
router.post('/:uuid/pay', authenticate, requireRole('admin'), idempotency, jobsActionsController.payJob);
router.post('/:uuid/cancel', authenticate, requireRole('admin', 'comercial'), idempotency, jobsActionsController.cancelJob);

router.post('/:uuid/regularize', authenticate, requireRole('admin', 'comercial'), idempotency, jobRegularizeController.regularizeJob);

router.post('/:uuid/materials', authenticate, requireRole('admin', 'trabajador'), idempotency, jobMaterialsController.addMaterial);
router.patch('/:uuid/materials/:material_uuid', authenticate, requireRole('admin'), idempotency, jobMaterialsController.updateMaterial);
router.delete('/:uuid/materials/:material_uuid', authenticate, requireRole('admin', 'trabajador'), idempotency, jobMaterialsController.removeMaterial);

router.post(
  '/:uuid/photos',
  authenticate,
  requireRole('admin', 'comercial', 'trabajador'),
  workerPhotoLimit,
  uploadPhotoMiddleware,
  idempotency,
  jobPhotosController.uploadPhoto
);
router.delete('/:uuid/photos/:photo_uuid', authenticate, requireRole('admin', 'comercial', 'trabajador'), idempotency, jobPhotosController.removePhoto);
router.get('/:uuid/photos/:photo_uuid/file', authenticate, jobPhotosController.servePhotoFile);

module.exports = router;
