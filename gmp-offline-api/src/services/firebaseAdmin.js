// src/services/firebaseAdmin.js
//
// Inicialización centralizada de Firebase Admin SDK (Fase 9).
// Se inicializa una sola vez (lazy) y se reutiliza en toda la app.
//
// Nota de versión: firebase-admin@14.x expone `admin.cert(...)` a nivel de
// paquete, no `admin.credential.cert(...)` (patrón de versiones viejas).
// Confirmado a mano en el VPS antes de escribir este archivo.

const fs = require('fs');
const admin = require('firebase-admin');

let initialized = false;

function getFirebaseAdmin() {
  if (!initialized) {
    const path = process.env.FIREBASE_SERVICE_ACCOUNT_PATH;
    if (!path) {
      throw new Error('FIREBASE_SERVICE_ACCOUNT_PATH no está configurada en el .env');
    }
    const raw = fs.readFileSync(path);
    const serviceAccount = JSON.parse(raw);
    admin.initializeApp({ credential: admin.cert(serviceAccount) });
    initialized = true;
  }
  return admin;
}

module.exports = { getFirebaseAdmin };
