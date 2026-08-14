// src/scripts/test_idempotency.js
//
// Prueba el middleware de idempotencia directamente contra la BD real,
// sin necesitar servidor HTTP ni login. Simula req/res/next.
//
// Uso:
//   cd /opt/gmp-offline-api
//   node src/scripts/test_idempotency.js
//
// Requiere que exista al menos un usuario admin en la empresa 1
// (el admin de prueba: phone 5551234, según avance-gmp-offline-first.md).
//
// Limpia sus propios datos de prueba en command_log al final.

const pool = require('../db/pool');
const idempotency = require('../middleware/idempotency');

function makeRes() {
  const res = {
    statusCode: 200,
    _body: null,
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(body) {
      this._body = body;
      console.log(`  -> respuesta: ${this.statusCode} ${JSON.stringify(body)}`);
      return this;
    },
  };
  return res;
}

async function runMiddleware(req) {
  const res = makeRes();
  let nextCalled = false;
  // idempotency es async: al llamar next() o res.json(), su función termina
  // y la promesa que retorna se resuelve. No hace falta timeout ni trucos.
  await idempotency(req, res, () => {
    nextCalled = true;
  });
  return { res, nextCalled };
}

async function main() {
  // 1. Buscar un usuario admin real de la empresa 1 para usar company_id/user_id válidos.
  const userResult = await pool.query(
    `SELECT id, company_id, role FROM users WHERE phone = '5551234' AND deleted_at IS NULL LIMIT 1`
  );
  if (userResult.rows.length === 0) {
    console.error('No se encontró el usuario de prueba (phone 5551234). Ajusta el script con un usuario real.');
    process.exit(1);
  }
  const testUser = userResult.rows[0];
  console.log(`Usando usuario de prueba: id=${testUser.id}, company_id=${testUser.company_id}, role=${testUser.role}`);

  const commandId = 'a1b2c3d4-1111-2222-3333-444455556666';
  const baseReq = {
    method: 'POST',
    baseUrl: '',
    path: '/test/idempotency',
    route: { path: '/test/idempotency' },
    user: { id: testUser.id, company_id: testUser.company_id },
    header(name) {
      return this.headers[name];
    },
  };

  // Limpieza previa por si se corrió antes
  await pool.query('DELETE FROM command_log WHERE command_uuid = $1', [commandId]);

  console.log('\n--- Caso 1: primera llamada (debe ejecutar next() y loguear) ---');
  const req1 = { ...baseReq, headers: { 'X-Command-Id': commandId }, body: { amount: 100, note: 'pago parcial' } };
  const { res: res1, nextCalled: next1 } = await runMiddleware(req1);
  if (next1) {
    // Simulamos lo que haría el controller real
    res1.status(201).json({ ok: true, message: 'job pagado' });
  }
  console.log(`  next() llamado: ${next1}`);

  await new Promise((r) => setTimeout(r, 300)); // dar tiempo al INSERT en background

  console.log('\n--- Caso 2: reintento con mismo X-Command-Id y mismo body (debe devolver la respuesta guardada, SIN llamar next()) ---');
  const req2 = { ...baseReq, headers: { 'X-Command-Id': commandId }, body: { amount: 100, note: 'pago parcial' } };
  const { nextCalled: next2 } = await runMiddleware(req2);
  console.log(`  next() llamado: ${next2} (esperado: false)`);

  console.log('\n--- Caso 3: mismo X-Command-Id, body DISTINTO (debe devolver 409 command_id_reused) ---');
  const req3 = { ...baseReq, headers: { 'X-Command-Id': commandId }, body: { amount: 999, note: 'monto cambiado' } };
  const { nextCalled: next3 } = await runMiddleware(req3);
  console.log(`  next() llamado: ${next3} (esperado: false)`);

  console.log('\n--- Caso 4: falta el header X-Command-Id (debe devolver 400 missing_command_id) ---');
  const req4 = { ...baseReq, headers: {}, body: { amount: 100 } };
  const { nextCalled: next4 } = await runMiddleware(req4);
  console.log(`  next() llamado: ${next4} (esperado: false)`);

  // Limpieza
  await pool.query('DELETE FROM command_log WHERE command_uuid = $1', [commandId]);
  console.log('\nLimpieza de command_log de prueba: OK');

  await pool.end();
}

main().catch((err) => {
  console.error('Error en test:', err);
  process.exit(1);
});
