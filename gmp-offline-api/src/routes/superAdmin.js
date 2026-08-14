const express = require('express');
const router = express.Router();
const ctrl = require('../controllers/superAdminController');
const { authenticateSuperAdmin } = require('../middleware/superAdminAuth');

router.post('/superadmin/login', ctrl.login);
router.post('/superadmin/companies', authenticateSuperAdmin, ctrl.createCompany);
router.get('/superadmin/companies', authenticateSuperAdmin, ctrl.listCompanies);
router.patch('/superadmin/companies/:company_id/status', authenticateSuperAdmin, ctrl.updateCompanyStatus);
router.delete('/superadmin/companies/:company_id', authenticateSuperAdmin, ctrl.deleteCompany);
router.post('/superadmin/companies/:company_id/admin', authenticateSuperAdmin, ctrl.createCompanyAdmin);
router.post('/superadmin/billing-reports', authenticateSuperAdmin, ctrl.generateBillingReport);
router.get('/superadmin/billing-reports', authenticateSuperAdmin, ctrl.listBillingReports);

module.exports = router;
