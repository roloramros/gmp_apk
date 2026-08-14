// src/config/storage.js
//
// Configuración de almacenamiento local de fotos de trabajo (Fase 3, sección 6.2).
// UPLOAD_ROOT es la carpeta raíz fuera de git donde se guardan los archivos.
// Se puede sobreescribir con la variable de entorno UPLOAD_DIR (agregar a .env
// si se quiere una ruta distinta a la default, p. ej. un disco separado en el VPS).

const path = require('path');

const UPLOAD_ROOT = process.env.UPLOAD_DIR
  ? path.resolve(process.env.UPLOAD_DIR)
  : path.resolve(__dirname, '../../uploads');

const JOB_PHOTOS_DIR = path.join(UPLOAD_ROOT, 'job_photos');

module.exports = { UPLOAD_ROOT, JOB_PHOTOS_DIR };
