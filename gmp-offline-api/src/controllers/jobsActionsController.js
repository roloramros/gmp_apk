// src/controllers/jobsActionsController.js
// Endpoints de acción sobre jobs (outbox de comandos).

const pool = require('../db/pool');
const { getFullJobByUuid } = require('./jobsController');
const { sendNotificationToUsers } = require('../services/notifications');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isValidUuid(v) {
  return typeof v === 'string' && UUID_RE.test(v);
}

async function withJobLock(companyId, jobUuid, fn) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const jobResult = await client.query(
      `SELECT id, uuid, status, scheduled_at, total_amount, amount_paid
       FROM jobs
       WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL
       FOR UPDATE`,
      [jobUuid, companyId]
    );
    if (jobResult.rows.length === 0) {
      await client.query('ROLLBACK');
      return { error: { status: 404, body: { error_code: 'not_found', message: 'Job no encontrado.' } } };
    }
    const outcome = await fn(client, jobResult.rows[0]);
    if (outcome && outcome.error) {
      await client.query('ROLLBACK');
      return outcome;
    }
    await client.query('COMMIT');
    return { ok: true };
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

function invalidTransition(currentStatus, action) {
  return { error: { status: 409, body: { error_code: 'invalid_status_transition', message: `No se puede ${action} un job en estado "${currentStatus}".` } } };
}

async function ensureWorkerIsAssigned(client, jobId, user) {
  if (user.role !== 'trabajador') return true;
  const result = await client.query(
    `SELECT 1 FROM job_workers WHERE job_id = $1 AND user_id = $2 AND deleted_at IS NULL`,
    [jobId, user.user_id]
  );
  return result.rows.length > 0;
}

async function reconcileAssignmentStatus(client, job) {
  if (!['pending', 'assigned'].includes(job.status)) return;
  const workers = await client.query(
    `SELECT 1 FROM job_workers WHERE job_id = $1 AND deleted_at IS NULL LIMIT 1`,
    [job.id]
  );
  const newStatus = job.scheduled_at && workers.rows.length > 0 ? 'assigned' : 'pending';
  if (newStatus !== job.status) {
    await client.query(`UPDATE jobs SET status = $1, updated_at = now() WHERE id = $2`, [newStatus, job.id]);
    job.status = newStatus;
  }
}

function jobLabel(job) {
  return job.client_name || job.title || 'el montaje';
}

async function getActorName(companyId, actorUserId) {
  const actorResult = await pool.query(
    `SELECT full_name FROM users
     WHERE id = $1 AND company_id = $2 AND deleted_at IS NULL`,
    [actorUserId, companyId]
  );
  return actorResult.rows[0] ? actorResult.rows[0].full_name : 'Un trabajador';
}

async function notifyJobStarted(companyId, actorUserId, job) {
  try {
    const [actorName, recipientsResult] = await Promise.all([
      getActorName(companyId, actorUserId),
      pool.query(
        `SELECT id FROM users
         WHERE company_id = $1
           AND role IN ('admin', 'comercial')
           AND active = true
           AND deleted_at IS NULL`,
        [companyId]
      ),
    ]);

    await sendNotificationToUsers(recipientsResult.rows.map((row) => row.id), {
      title: 'Montaje iniciado',
      body: `${actorName} inició el montaje de ${jobLabel(job)}`,
      data: { type: 'job_started', job_uuid: job.uuid },
    });
  } catch (err) {
    console.error('[jobsActions] Error preparando notificación de inicio:', err);
  }
}

async function notifyJobFinished(companyId, actorUserId, job) {
  try {
    const [actorName, recipientsResult] = await Promise.all([
      getActorName(companyId, actorUserId),
      pool.query(
        `SELECT DISTINCT recipient_id AS id
         FROM (
           SELECT u.id AS recipient_id FROM users u
           WHERE u.company_id = $1 AND u.role = 'admin' AND u.active = true AND u.deleted_at IS NULL
           UNION
           SELECT u.id AS recipient_id
           FROM jobs j
           JOIN job_workers jw ON jw.job_id = j.id AND jw.deleted_at IS NULL
           JOIN users u ON u.id = jw.user_id AND u.company_id = j.company_id
           WHERE j.uuid = $2 AND j.company_id = $1 AND j.deleted_at IS NULL
             AND u.role = 'trabajador' AND u.active = true AND u.deleted_at IS NULL
         ) recipients`,
        [companyId, job.uuid]
      ),
    ]);
    await sendNotificationToUsers(recipientsResult.rows.map((row) => row.id), {
      title: 'Montaje finalizado',
      body: `${actorName} finalizó el montaje de ${jobLabel(job)}`,
      data: { type: 'job_finished', job_uuid: job.uuid },
    });
  } catch (err) {
    console.error('[jobsActions] Error preparando notificación de finalización:', err);
  }
}

async function assignWorker(req, res) {
  const { uuid } = req.params;
  const { user_uuid } = req.body || {};
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  if (!isValidUuid(user_uuid)) return res.status(400).json({ error_code: 'invalid_user_uuid', message: 'user_uuid es requerido y debe ser un UUID válido.' });
  try {
    const workerResult = await pool.query(
      `SELECT id, active FROM users WHERE uuid = $1 AND company_id = $2 AND role IN ('trabajador', 'admin') AND deleted_at IS NULL`,
      [user_uuid, req.user.company_id]
    );
    if (workerResult.rows.length === 0) return res.status(400).json({ error_code: 'worker_not_found', message: 'No se encontró personal asignable con ese user_uuid en la empresa.' });
    const worker = workerResult.rows[0];
    if (!worker.active) return res.status(400).json({ error_code: 'worker_inactive', message: 'El trabajador está desactivado.' });
    let shouldNotifyWorker = false;
    const result = await withJobLock(req.user.company_id, uuid, async (client, job) => {
      if (['cancelled', 'paid'].includes(job.status)) return invalidTransition(job.status, 'asignar trabajadores a');
      const existing = await client.query(`SELECT id, deleted_at FROM job_workers WHERE job_id = $1 AND user_id = $2`, [job.id, worker.id]);
      if (existing.rows.length === 0) {
        await client.query(`INSERT INTO job_workers (uuid, company_id, job_id, user_id, created_by_device_id) VALUES (gen_random_uuid(), $1, $2, $3, $4)`, [req.user.company_id, job.id, worker.id, req.body.created_by_device_id || null]);
        shouldNotifyWorker = true;
      } else if (existing.rows[0].deleted_at !== null) {
        await client.query(`UPDATE job_workers SET deleted_at = NULL, updated_at = now() WHERE id = $1`, [existing.rows[0].id]);
        shouldNotifyWorker = true;
      }
      await reconcileAssignmentStatus(client, job);
    });
    if (result.error) return res.status(result.error.status).json(result.error.body);
    const fullJob = await getFullJobByUuid(uuid);
    if (shouldNotifyWorker) {
      const address = (fullJob && fullJob.address) || 'Dirección no especificada';
      const description = (fullJob && fullJob.description) || 'Sin descripción del trabajo';
      await sendNotificationToUsers([worker.id], {
        title: 'Nuevo Montaje Asignado',
        body: `${address}\n${description}`,
        data: { type: 'job_assigned', job_uuid: uuid },
      });
    }
    return res.status(200).json(fullJob);
  } catch (err) {
    console.error('[jobsActions] Error en assignWorker:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al asignar trabajador.' });
  }
}

async function unassignWorker(req, res) {
  const { uuid } = req.params;
  const { user_uuid } = req.body || {};
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  if (!isValidUuid(user_uuid)) return res.status(400).json({ error_code: 'invalid_user_uuid', message: 'user_uuid es requerido y debe ser un UUID válido.' });
  try {
    const workerResult = await pool.query(`SELECT id FROM users WHERE uuid = $1 AND company_id = $2 AND role IN ('trabajador', 'admin')`, [user_uuid, req.user.company_id]);
    if (workerResult.rows.length === 0) return res.status(400).json({ error_code: 'worker_not_found', message: 'No se encontró personal asignable con ese user_uuid en la empresa.' });
    const workerId = workerResult.rows[0].id;
    const result = await withJobLock(req.user.company_id, uuid, async (client, job) => {
      const updateResult = await client.query(`UPDATE job_workers SET deleted_at = now(), updated_at = now() WHERE job_id = $1 AND user_id = $2 AND deleted_at IS NULL RETURNING id`, [job.id, workerId]);
      if (updateResult.rows.length === 0) return { error: { status: 404, body: { error_code: 'not_assigned', message: 'Ese trabajador no está asignado a este job.' } } };
      await reconcileAssignmentStatus(client, job);
    });
    if (result.error) return res.status(result.error.status).json(result.error.body);
    return res.status(200).json(await getFullJobByUuid(uuid));
  } catch (err) {
    console.error('[jobsActions] Error en unassignWorker:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al quitar trabajador.' });
  }
}

async function startJob(req, res) {
  const { uuid } = req.params;
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  try {
    const result = await withJobLock(req.user.company_id, uuid, async (client, job) => {
      const assigned = await ensureWorkerIsAssigned(client, job.id, req.user);
      if (!assigned) return { error: { status: 403, body: { error_code: 'forbidden', message: 'No estás asignado a este job.' } } };
      if (job.status !== 'assigned') return invalidTransition(job.status, 'iniciar');
      await client.query(`UPDATE jobs SET status = 'in_progress', started_at = now(), updated_at = now() WHERE id = $1`, [job.id]);
    });
    if (result.error) return res.status(result.error.status).json(result.error.body);
    const fullJob = await getFullJobByUuid(uuid);
    await notifyJobStarted(req.user.company_id, req.user.user_id, fullJob);
    return res.status(200).json(fullJob);
  } catch (err) {
    console.error('[jobsActions] Error en startJob:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al iniciar el job.' });
  }
}

async function finishJob(req, res) {
  const { uuid } = req.params;
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  try {
    const result = await withJobLock(req.user.company_id, uuid, async (client, job) => {
      const assigned = await ensureWorkerIsAssigned(client, job.id, req.user);
      if (!assigned) return { error: { status: 403, body: { error_code: 'forbidden', message: 'No estás asignado a este job.' } } };
      if (job.status !== 'in_progress') return invalidTransition(job.status, 'finalizar');
      await client.query(`UPDATE jobs SET status = 'finished', finished_at = now(), updated_at = now() WHERE id = $1`, [job.id]);
    });
    if (result.error) return res.status(result.error.status).json(result.error.body);
    const fullJob = await getFullJobByUuid(uuid);
    await notifyJobFinished(req.user.company_id, req.user.user_id, fullJob);
    return res.status(200).json(fullJob);
  } catch (err) {
    console.error('[jobsActions] Error en finishJob:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al finalizar el job.' });
  }
}

async function invoiceJob(req, res) {
  const { uuid } = req.params;
  const { total_amount } = req.body || {};
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  const amount = Number(total_amount);
  if (!Number.isFinite(amount) || amount <= 0) return res.status(400).json({ error_code: 'invalid_amount', message: 'total_amount debe ser un número mayor a 0.' });
  try {
    const result = await withJobLock(req.user.company_id, uuid, async (client, job) => {
      if (job.status !== 'finished') return invalidTransition(job.status, 'facturar');
      await client.query(`UPDATE jobs SET status = 'invoiced', invoiced_at = now(), total_amount = $1, updated_at = now() WHERE id = $2`, [amount, job.id]);
    });
    if (result.error) return res.status(result.error.status).json(result.error.body);
    return res.status(200).json(await getFullJobByUuid(uuid));
  } catch (err) {
    console.error('[jobsActions] Error en invoiceJob:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al facturar el job.' });
  }
}

async function payJob(req, res) {
  const { uuid } = req.params;
  const { amount: rawAmount } = req.body || {};
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  const amount = Number(rawAmount);
  if (!Number.isFinite(amount) || amount <= 0) return res.status(400).json({ error_code: 'invalid_amount', message: 'amount debe ser un número mayor a 0.' });
  try {
    const result = await withJobLock(req.user.company_id, uuid, async (client, job) => {
      if (job.status === 'paid') return { error: { status: 409, body: { error_code: 'already_paid', message: 'Este job ya está pagado en su totalidad.' } } };
      if (!['invoiced', 'partially_paid'].includes(job.status)) return invalidTransition(job.status, 'pagar');
      const totalAmount = Number(job.total_amount);
      const currentPaid = Number(job.amount_paid);
      const newPaid = currentPaid + amount;
      if (newPaid > totalAmount + 0.0001) return { error: { status: 400, body: { error_code: 'overpayment', message: `El monto excede el saldo pendiente (saldo: ${(totalAmount - currentPaid).toFixed(2)}).` } } };
      const newStatus = Math.abs(newPaid - totalAmount) < 0.0001 ? 'paid' : 'partially_paid';
      await client.query(`UPDATE jobs SET amount_paid = $1, status = $2, updated_at = now() WHERE id = $3`, [newPaid, newStatus, job.id]);
    });
    if (result.error) return res.status(result.error.status).json(result.error.body);
    return res.status(200).json(await getFullJobByUuid(uuid));
  } catch (err) {
    console.error('[jobsActions] Error en payJob:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al registrar el pago.' });
  }
}

async function cancelJob(req, res) {
  const { uuid } = req.params;
  if (!isValidUuid(uuid)) return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  try {
    const result = await withJobLock(req.user.company_id, uuid, async (client, job) => {
      if (!['pending', 'assigned'].includes(job.status)) return invalidTransition(job.status, 'cancelar (ya fue iniciado)');
      await client.query(`UPDATE jobs SET status = 'cancelled', cancelled_at = now(), updated_at = now() WHERE id = $1`, [job.id]);
    });
    if (result.error) return res.status(result.error.status).json(result.error.body);
    return res.status(200).json(await getFullJobByUuid(uuid));
  } catch (err) {
    console.error('[jobsActions] Error en cancelJob:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al cancelar el job.' });
  }
}

module.exports = { assignWorker, unassignWorker, startJob, finishJob, invoiceJob, payJob, cancelJob };
