// src/services/notifications.js
// Servicio reutilizable de notificaciones push (Fase 9).
// Nunca propaga errores al flujo principal: una falla de FCM no debe romper
// assign/start/finish ni dejar una transacción abierta esperando red.

const pool = require('../db/pool');
const { getFirebaseAdmin } = require('./firebaseAdmin');

const INVALID_TOKEN_CODES = new Set([
  'messaging/invalid-registration-token',
  'messaging/registration-token-not-registered',
]);

function normalizeData(data) {
  if (!data || typeof data !== 'object') return undefined;
  const normalized = {};
  for (const [key, value] of Object.entries(data)) {
    if (value === undefined || value === null) continue;
    normalized[key] = String(value);
  }
  return Object.keys(normalized).length > 0 ? normalized : undefined;
}

async function sendNotificationToUsers(userIds, { title, body, data } = {}) {
  try {
    const ids = [...new Set((userIds || []).filter((id) => Number.isInteger(Number(id))).map(Number))];
    if (ids.length === 0) return { sent: 0, failed: 0, removedInvalidTokens: 0 };

    const tokenResult = await pool.query(
      `SELECT fcm_token
       FROM device_tokens
       WHERE user_id = ANY($1::bigint[])`,
      [ids]
    );

    const tokens = [...new Set(tokenResult.rows.map((row) => row.fcm_token).filter(Boolean))];
    if (tokens.length === 0) {
      console.log(`[notifications] Sin tokens FCM para user_ids=${ids.join(',')}`);
      return { sent: 0, failed: 0, removedInvalidTokens: 0 };
    }

    const admin = getFirebaseAdmin();
    const invalidTokens = [];
    let sent = 0;
    let failed = 0;

    // Firebase admite hasta 500 tokens por multicast.
    for (let offset = 0; offset < tokens.length; offset += 500) {
      const chunk = tokens.slice(offset, offset + 500);
      const response = await admin.messaging().sendEachForMulticast({
        tokens: chunk,
        notification: {
          title: String(title || ''),
          body: String(body || ''),
        },
        data: normalizeData(data),
      });

      sent += response.successCount;
      failed += response.failureCount;

      response.responses.forEach((item, index) => {
        if (item.success) return;
        const code = item.error && item.error.code;
        if (INVALID_TOKEN_CODES.has(code)) invalidTokens.push(chunk[index]);
        console.error(`[notifications] FCM error token=${chunk[index]} code=${code || 'unknown'}`);
      });
    }

    const uniqueInvalidTokens = [...new Set(invalidTokens)];
    if (uniqueInvalidTokens.length > 0) {
      await pool.query(
        `DELETE FROM device_tokens WHERE fcm_token = ANY($1::text[])`,
        [uniqueInvalidTokens]
      );
      console.log(`[notifications] Tokens FCM inválidos eliminados: ${uniqueInvalidTokens.length}`);
    }

    console.log(`[notifications] Envío terminado: sent=${sent} failed=${failed} tokens=${tokens.length}`);
    return { sent, failed, removedInvalidTokens: uniqueInvalidTokens.length };
  } catch (err) {
    console.error('[notifications] Error enviando notificación FCM:', err);
    return { sent: 0, failed: 0, removedInvalidTokens: 0, error: true };
  }
}

module.exports = { sendNotificationToUsers };
