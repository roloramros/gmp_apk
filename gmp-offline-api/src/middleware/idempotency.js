// src/middleware/idempotency.js
//
// Middleware de idempotencia para endpoints de acción (start, finish, pay,
// addMaterial, uploadPhoto, etc.), según el contrato cerrado en
// fase1-diseno-datos-sync.md (sección 4).
//
// Requiere ejecutarse DESPUÉS de `authenticate` (usa req.user.company_id y
// req.user.id / req.user.user_id).
//
// Uso en rutas:
//   router.post('/jobs/:uuid/start', authenticate, idempotency, jobsController.start);
//
// El controller no necesita saber nada de esto: responde normalmente con
// res.status(x).json(y) y este middleware guarda esa respuesta en
// command_log automáticamente, asociada al X-Command-Id recibido.

const crypto = require('crypto');
const pool = require('../db/pool');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Canonicaliza un valor JSON: ordena claves de objetos recursivamente.
 * Arrays mantienen su orden (el orden es parte del significado del payload).
 */
function canonicalize(value) {
  if (Array.isArray(value)) {
    return value.map(canonicalize);
  }
  if (value !== null && typeof value === 'object') {
    const sortedKeys = Object.keys(value).sort();
    const result = {};
    for (const key of sortedKeys) {
      result[key] = canonicalize(value[key]);
    }
    return result;
  }
  return value;
}

/**
 * SHA-256 del body JSON canonicalizado (claves ordenadas, sin espacios),
 * tal como se cerró en fase1-diseno-datos-sync.md sección 4.
 */
function computePayloadHash(body) {
  const canonical = canonicalize(body || {});
  const json = JSON.stringify(canonical); // JSON.stringify sin segundo/tercer arg no añade espacios
  return crypto.createHash('sha256').update(json).digest('hex');
}

async function idempotency(req, res, next) {
  try {
    const commandId = req.header('X-Command-Id');

    if (!commandId) {
      return res.status(400).json({
        error_code: 'missing_command_id',
        message: 'Header X-Command-Id es requerido en endpoints de acción.',
      });
    }

    if (!UUID_RE.test(commandId)) {
      return res.status(400).json({
        error_code: 'invalid_command_id',
        message: 'X-Command-Id debe ser un UUID válido.',
      });
    }

    if (!req.user || !req.user.company_id) {
      // Esto indica un error de orden de middlewares (idempotency antes que authenticate)
      return res.status(500).json({
        error_code: 'idempotency_misconfigured',
        message: 'idempotency middleware requiere ejecutarse después de authenticate.',
      });
    }

    const payloadHash = computePayloadHash(req.body);
    const endpoint = `${req.method} ${req.baseUrl}${req.route ? req.route.path : req.path}`;

    const existing = await pool.query(
      `SELECT command_uuid, payload_hash, result_status, result_body
       FROM command_log
       WHERE command_uuid = $1`,
      [commandId]
    );

    if (existing.rows.length > 0) {
      const row = existing.rows[0];

      if (row.payload_hash === payloadHash) {
        // Reintento legítimo: mismo comando, mismo payload -> devolver lo ya guardado.
        return res.status(row.result_status).json(row.result_body);
      }

      // Mismo command_uuid, payload distinto -> señal de bug de cliente, fallar explícito.
      return res.status(409).json({
        error_code: 'command_id_reused',
        message: 'Este X-Command-Id ya fue usado con un payload diferente.',
      });
    }

    // No existe todavía: dejamos pasar al controller, pero interceptamos
    // res.json para persistir el resultado en command_log una sola vez.
    const originalJson = res.json.bind(res);
    let alreadyLogged = false;

    res.json = (body) => {
      if (!alreadyLogged) {
        alreadyLogged = true;
        const statusCode = res.statusCode || 200;
        const userId = req.user.id || req.user.user_id;

        // Guardado en background: no bloqueamos la respuesta al cliente por esto,
        // pero sí logueamos el error si falla, ya que rompe la garantía de idempotencia.
        pool
          .query(
            `INSERT INTO command_log
               (company_id, user_id, command_uuid, endpoint, payload_hash, result_status, result_body)
             VALUES ($1, $2, $3, $4, $5, $6, $7)
             ON CONFLICT (command_uuid) DO NOTHING`,
            [req.user.company_id, userId, commandId, endpoint, payloadHash, statusCode, body]
          )
          .catch((err) => {
            console.error(`[idempotency] Fallo al guardar command_log para ${commandId}:`, err);
          });
      }
      return originalJson(body);
    };

    next();
  } catch (err) {
    console.error('[idempotency] Error inesperado:', err);
    res.status(500).json({ error_code: 'idempotency_internal_error', message: 'Error interno de idempotencia.' });
  }
}

module.exports = idempotency;
