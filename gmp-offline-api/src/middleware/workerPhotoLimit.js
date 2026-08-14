const pool = require('../db/pool');

async function workerPhotoLimit(req, res, next) {
  if (!req.user || req.user.role !== 'trabajador') return next();

  try {
    const jobResult = await pool.query(
      `SELECT id FROM jobs
       WHERE uuid = $1 AND company_id = $2 AND deleted_at IS NULL`,
      [req.params.uuid, req.user.company_id]
    );
    const job = jobResult.rows[0];
    if (!job) return res.status(404).json({ error_code: 'not_found', message: 'Job no encontrado.' });

    const countResult = await pool.query(
      `SELECT COUNT(*)::int AS total
       FROM job_photos
       WHERE job_id = $1
         AND company_id = $2
         AND uploaded_by_user_id = $3
         AND deleted_at IS NULL`,
      [job.id, req.user.company_id, req.user.user_id]
    );

    if ((countResult.rows[0]?.total || 0) >= 3) {
      return res.status(409).json({
        error_code: 'worker_photo_limit',
        message: 'El trabajador ya agregó las 3 fotos permitidas para este trabajo.',
      });
    }

    return next();
  } catch (err) {
    console.error('[workerPhotoLimit] Error validando límite de fotos:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al validar el límite de fotos.' });
  }
}

module.exports = workerPhotoLimit;
