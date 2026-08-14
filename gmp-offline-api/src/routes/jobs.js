// src/routes/jobs.js

const express = require('express');
const router = express.Router();

const { authenticate, requireRole } = require('../middleware/auth');
const idempotency = require('../middleware/idempotency');
const { uploadPhotoMiddleware } = require('../middleware/upload');
const jobsController = require('../controllers/jobsController');
const jobsActionsController = require('../controllers/jobsActionsController');
const jobMaterialsController = require('../controllers/jobMaterialsController');
const jobPhotosController = require('../controllers/jobPhotosController');

// Lectura: cualquier rol autenticado; la visibilidad por rol se filtra
// dentro del controller (admin/comercial ven todo, trabajador/cliente solo lo suyo).
router.get('/', authenticate, jobsController.listJobs);
router.get('/:uuid', authenticate, jobsController.getJob);

// Escritura: solo admin y comercial. Requiere X-Command-Id (idempotencia).
router.post('/', authenticate, requireRole('admin', 'comercial'), idempotency, jobsController.createJob);
router.patch('/:uuid', authenticate, requireRole('admin', 'comercial'), idempotency, jobsController.updateJob);

// Acciones de estado (outbox de comandos).
// assign/unassign/invoice/pay: solo admin.
// start/finish: admin o trabajador asignado (comercial NO puede).
// cancel: admin/comercial, y solo si el job no fue iniciado (regla en el controller).
router.post('/:uuid/assign', authenticate, requireRole('admin'), idempotency, jobsActionsController.assignWorker);
router.post('/:uuid/unassign', authenticate, requireRole('admin'), idempotency, jobsActionsController.unassignWorker);
router.post('/:uuid/start', authenticate, requireRole('admin', 'trabajador'), idempotency, jobsActionsController.startJob);
router.post('/:uuid/finish', authenticate, requireRole('admin', 'trabajador'), idempotency, jobsActionsController.finishJob);
router.post('/:uuid/invoice', authenticate, requireRole('admin'), idempotency, jobsActionsController.invoiceJob);
router.post('/:uuid/pay', authenticate, requireRole('admin'), idempotency, jobsActionsController.payJob);
router.post('/:uuid/cancel', authenticate, requireRole('admin', 'comercial'), idempotency, jobsActionsController.cancelJob);

// Materiales usados en el job: admin, o trabajador asignado (comercial NO puede,
// mismo criterio que start/finish; la validación de asignación vive en el controller).
router.post('/:uuid/materials', authenticate, requireRole('admin', 'trabajador'), idempotency, jobMaterialsController.addMaterial);
router.delete('/:uuid/materials/:material_uuid', authenticate, requireRole('admin', 'trabajador'), idempotency, jobMaterialsController.removeMaterial);

// Fotos de trabajo (multipart, campo "photo"). Escritura: admin o trabajador
// asignado (mismo criterio que materiales). Multer corre ANTES de idempotency
// para que req.body (campos de texto del form) ya esté poblado al calcular el hash.
router.post(
  '/:uuid/photos',
  authenticate,
  requireRole('admin', 'comercial', 'trabajador'),
  uploadPhotoMiddleware,
  idempotency,
  jobPhotosController.uploadPhoto
);
router.delete('/:uuid/photos/:photo_uuid', authenticate, requireRole('admin', 'comercial', 'trabajador'), idempotency, jobPhotosController.removePhoto);

// Servido del archivo: lectura autenticada, sin restricción de rol a nivel ruta
// (la visibilidad se resuelve dentro del controller: admin/comercial ven todo,
// trabajador solo sus jobs asignados, cliente solo los suyos). No lleva
// X-Command-Id porque es un GET, no un comando de escritura.
router.get('/:uuid/photos/:photo_uuid/file', authenticate, jobPhotosController.servePhotoFile);

module.exports = router;
