// src/controllers/deviceTokensController.js
//
// Alta/baja de tokens FCM por usuario (Fase 9).
// No pasa por idempotencia (X-Command-Id): es un upsert natural sobre
// `fcm_token` (UNIQUE), reintentar no duplica ni tiene efecto colateral
// distinto a "guardar el mismo dato de nuevo" — mismo criterio que ya
// se usó para /staff (ver avance-fase6-paso1.md, Paso 7).

const pool = require('../db/pool');

// POST /device-tokens
// body: { fcm_token, platform? }  (platform default 'android')
async function registerDeviceToken(req, res) {
  const { fcm_token, platform } = req.body || {};

  if (!fcm_token || typeof fcm_token !== 'string' || !fcm_token.trim()) {
    return res.status(400).json({ error_code: 'invalid_fcm_token', message: 'fcm_token es requerido.' });
  }

  try {
    // Upsert por fcm_token: si el token ya existía (mismo dispositivo,
    // usuario distinto porque cambió de sesión), se reasigna a este user_id.
    const result = await pool.query(
      `INSERT INTO device_tokens (user_id, fcm_token, platform)
       VALUES ($1, $2, $3)
       ON CONFLICT (fcm_token)
       DO UPDATE SET user_id = EXCLUDED.user_id,
                      platform = EXCLUDED.platform,
                      updated_at = now()
       RETURNING id, user_id, fcm_token, platform, updated_at`,
      [req.user.user_id, fcm_token.trim(), platform || 'android']
    );
    return res.status(200).json(result.rows[0]);
  } catch (err) {
    console.error('[device-tokens] Error en registerDeviceToken:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al registrar el token.' });
  }
}

// DELETE /device-tokens
// body: { fcm_token }
// Se usa al cerrar sesión, para que ese dispositivo deje de recibir
// notificaciones del usuario que se deslogueó.
async function unregisterDeviceToken(req, res) {
  const { fcm_token } = req.body || {};

  if (!fcm_token || typeof fcm_token !== 'string' || !fcm_token.trim()) {
    return res.status(400).json({ error_code: 'invalid_fcm_token', message: 'fcm_token es requerido.' });
  }

  try {
    // Solo borra si el token pertenece al usuario autenticado (evita que
    // un usuario borre el token de otro).
    const result = await pool.query(
      `DELETE FROM device_tokens WHERE fcm_token = $1 AND user_id = $2 RETURNING id`,
      [fcm_token.trim(), req.user.user_id]
    );
    if (result.rowCount === 0) {
      return res.status(404).json({ error_code: 'not_found', message: 'Token no encontrado para este usuario.' });
    }
    return res.status(200).json({ ok: true });
  } catch (err) {
    console.error('[device-tokens] Error en unregisterDeviceToken:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al eliminar el token.' });
  }
}

module.exports = { registerDeviceToken, unregisterDeviceToken };
