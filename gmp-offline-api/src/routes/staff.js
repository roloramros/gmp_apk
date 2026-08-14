const express = require('express');
const router = express.Router();
const staffController = require('../controllers/staffController');
const { authenticate, requireRole } = require('../middleware/auth');

router.post('/staff', authenticate, requireRole('admin'), staffController.createStaff);
router.get('/staff', authenticate, requireRole('admin', 'comercial'), staffController.listStaff);
router.post('/staff/:uuid/deactivate', authenticate, requireRole('admin'), staffController.deactivateStaff);
router.get('/staff/:uuid/report', authenticate, requireRole('admin', 'comercial'), staffController.staffReport);

module.exports = router;
