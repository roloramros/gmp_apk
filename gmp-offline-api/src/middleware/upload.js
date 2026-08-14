// src/middleware/upload.js
//
// Configuración de multer para POST /jobs/:uuid/photos (campo "photo").
//
// Usa almacenamiento en MEMORIA (no disco directo): el controller decide
// dónde escribir el archivo recién después de validar el job (empresa,
// estado, rol/asignación), para no dejar archivos huérfanos en disco si la
// request se rechaza por una regla de negocio.
//
// Requiere el paquete "multer" (no estaba entre las dependencias hasta este
// paso): npm install multer

const multer = require('multer');

const ALLOWED_MIME_TYPES = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
};

const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

const multerUpload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_FILE_SIZE_BYTES, files: 1 },
  fileFilter: (req, file, cb) => {
    if (!ALLOWED_MIME_TYPES[file.mimetype]) {
      const err = new Error('unsupported_file_type');
      err.code = 'UNSUPPORTED_FILE_TYPE';
      return cb(err);
    }
    cb(null, true);
  },
}).single('photo');

// Envuelve multer para devolver errores en el mismo formato { error_code, message }
// que el resto de la API, en vez del error HTML default de Express.
function uploadPhotoMiddleware(req, res, next) {
  multerUpload(req, res, (err) => {
    if (!err) return next();

    if (err.code === 'UNSUPPORTED_FILE_TYPE') {
      return res.status(400).json({
        error_code: 'unsupported_file_type',
        message: 'Formato de imagen no soportado. Usar JPEG, PNG o WEBP.',
      });
    }
    if (err.code === 'LIMIT_FILE_SIZE') {
      return res.status(400).json({
        error_code: 'file_too_large',
        message: `El archivo supera el máximo permitido (${MAX_FILE_SIZE_BYTES / (1024 * 1024)} MB).`,
      });
    }
    console.error('[upload] Error de multer:', err);
    return res.status(400).json({ error_code: 'upload_error', message: 'Error al procesar el archivo subido.' });
  });
}

module.exports = { uploadPhotoMiddleware, ALLOWED_MIME_TYPES, MAX_FILE_SIZE_BYTES };
