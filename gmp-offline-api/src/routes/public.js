// src/routes/public.js
//
// Rutas SIN autenticación. Ojo al agregar algo acá: todo lo que se monte
// en este router es accesible por cualquiera sin token.

const express = require('express');
const router = express.Router();
const publicCatalogController = require('../controllers/publicCatalogController');

router.get('/catalog/:slug', publicCatalogController.getPublicCatalog);
router.get('/catalog/:slug/photos/:photo_uuid/file', publicCatalogController.servePublicPhotoFile);

module.exports = router;
