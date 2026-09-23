// src/routes/public.js
//
// Rutas SIN autenticación. Ojo al agregar algo acá: todo lo que se monte
// en este router es accesible por cualquiera sin token.

const express = require('express');
const router = express.Router();
const publicCatalogController = require('../controllers/publicCatalogController');
const publicStoreController = require('../controllers/publicStoreController');
const publicGalleryController = require('../controllers/publicGalleryController');

router.get('/catalog/:slug', publicCatalogController.getPublicCatalog);
router.get('/catalog/:slug/photos/:photo_uuid/file', publicCatalogController.servePublicPhotoFile);

router.get('/store/:slug', publicStoreController.getPublicStore);
router.get('/store/:slug/photos/:photo_uuid/file', publicStoreController.servePublicPhotoFile);

router.get('/gallery/:slug', publicGalleryController.getPublicGallery);
router.get('/gallery/:slug/photos/:photo_uuid/file', publicGalleryController.servePublicPhotoFile);

module.exports = router;
