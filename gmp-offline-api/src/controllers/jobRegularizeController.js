const pool = require('../db/pool');
const { getFullJobByUuid } = require('./jobsController');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ALLOWED_TARGETS = new Set(['in_progress', 'finished', 'invoiced', 'paid', 'cancelled']);

async function regularizeJob(req, res) {
  const { uuid } = req.params;
  const { status: targetStatus } = req.body || {};

  if (!UUID_RE.test(uuid || '')) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid inválido en la URL.' });
  }
  if (!ALLOWED_TARGETS.has(targetStatus)) {
    return res.status(400).json({ error_code: 'invalid_status', message: 'Estado destino no permitido para regularización.' });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await client.query(
      `SELECT id, status, scheduled_at, proposed_date, price
       FROM jobs
       WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL
       FOR UPDATE`,
      [uuid, req.user.company_id]
    );

    if (result.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error_code: 'not_found', message: 'Job no encontrado.' });
    }

    const job = result.rows[0];
    if (!['pending', 'assigned'].includes(job.status)) {
      await client.query('ROLLBACK');
      return res.status(409).json({
        error_code: 'invalid_status_transition',
        message: `Solo se pueden regularizar montajes pendientes o asignados. Estado actual: "${job.status}".`,
      });
    }

    if (targetStatus === 'cancelled') {
      await client.query(
        `UPDATE jobs
         SET status = 'cancelled', cancelled_at = now(), updated_at = now()
         WHERE id = $1`,
        [job.id]
      );
    } else {
      const officialDate = job.scheduled_at || job.proposed_date;
      if (!officialDate) {
        await client.query('ROLLBACK');
        return res.status(400).json({
          error_code: 'missing_date',
          message: 'El montaje necesita una fecha propuesta u oficial para regularizarse.',
        });
      }

      const price = Number(job.price);
      if (['invoiced', 'paid'].includes(targetStatus) && (!Number.isFinite(price) || price <= 0)) {
        await client.query('ROLLBACK');
        return res.status(400).json({
          error_code: 'invalid_price',
          message: 'El montaje necesita un precio inicial válido para marcarse como facturado o pagado.',
        });
      }

      if (targetStatus === 'in_progress') {
        await client.query(
          `UPDATE jobs
           SET scheduled_at = $1, status = 'in_progress', started_at = COALESCE(started_at, $1), updated_at = now()
           WHERE id = $2`,
          [officialDate, job.id]
        );
      } else if (targetStatus === 'finished') {
        await client.query(
          `UPDATE jobs
           SET scheduled_at = $1, status = 'finished',
               started_at = COALESCE(started_at, $1), finished_at = COALESCE(finished_at, $1), updated_at = now()
           WHERE id = $2`,
          [officialDate, job.id]
        );
      } else if (targetStatus === 'invoiced') {
        await client.query(
          `UPDATE jobs
           SET scheduled_at = $1, status = 'invoiced',
               started_at = COALESCE(started_at, $1), finished_at = COALESCE(finished_at, $1),
               invoiced_at = COALESCE(invoiced_at, $1), total_amount = $2, amount_paid = 0, updated_at = now()
           WHERE id = $3`,
          [officialDate, price, job.id]
        );
      } else if (targetStatus === 'paid') {
        await client.query(
          `UPDATE jobs
           SET scheduled_at = $1, status = 'paid',
               started_at = COALESCE(started_at, $1), finished_at = COALESCE(finished_at, $1),
               invoiced_at = COALESCE(invoiced_at, $1), total_amount = $2, amount_paid = $2, updated_at = now()
           WHERE id = $3`,
          [officialDate, price, job.id]
        );
      }
    }

    await client.query('COMMIT');
    return res.status(200).json(await getFullJobByUuid(uuid));
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('[jobRegularize] Error:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al regularizar el montaje.' });
  } finally {
    client.release();
  }
}

module.exports = { regularizeJob };
