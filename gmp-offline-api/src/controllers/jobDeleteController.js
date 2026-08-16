const fs = require('fs/promises');
const path = require('path');
const pool = require('../db/pool');
const { JOB_PHOTOS_DIR } = require('../config/storage');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

async function deleteJob(req, res) {
  const { uuid: jobUuid } = req.params;
  if (!UUID_RE.test(jobUuid || '')) {
    return res.status(400).json({ error_code: 'invalid_uuid', message: 'uuid de job inválido.' });
  }

  const client = await pool.connect();
  let photoDir = null;
  try {
    await client.query('BEGIN');

    const jobResult = await client.query(
      `SELECT id, uuid FROM jobs WHERE uuid = $1 AND company_id = $2 FOR UPDATE`,
      [jobUuid, req.user.company_id]
    );
    if (jobResult.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error_code: 'not_found', message: 'Montaje no encontrado.' });
    }

    const jobId = jobResult.rows[0].id;
    const [workers, materials, photos] = await Promise.all([
      client.query('SELECT uuid FROM job_workers WHERE job_id = $1', [jobId]),
      client.query('SELECT uuid FROM job_materials WHERE job_id = $1', [jobId]),
      client.query('SELECT uuid FROM job_photos WHERE job_id = $1', [jobId]),
    ]);

    const tombstones = [
      ...workers.rows.map((r) => ['job_workers', r.uuid]),
      ...materials.rows.map((r) => ['job_materials', r.uuid]),
      ...photos.rows.map((r) => ['job_photos', r.uuid]),
      ['jobs', jobUuid],
    ];

    for (const [entityName, entityUuid] of tombstones) {
      await client.query(
        `INSERT INTO sync_deletions (company_id, entity_name, entity_uuid, deleted_at)
         VALUES ($1, $2, $3, now())
         ON CONFLICT (company_id, entity_name, entity_uuid)
         DO UPDATE SET deleted_at = EXCLUDED.deleted_at`,
        [req.user.company_id, entityName, entityUuid]
      );
    }

    await client.query('DELETE FROM job_photos WHERE job_id = $1', [jobId]);
    await client.query('DELETE FROM job_materials WHERE job_id = $1', [jobId]);
    await client.query('DELETE FROM job_workers WHERE job_id = $1', [jobId]);
    await client.query('DELETE FROM jobs WHERE id = $1', [jobId]);

    await client.query('COMMIT');

    photoDir = path.join(JOB_PHOTOS_DIR, String(req.user.company_id), jobUuid);
    await fs.rm(photoDir, { recursive: true, force: true }).catch((err) => {
      console.error('[jobDelete] No se pudo borrar el directorio de fotos:', err);
    });

    return res.status(200).json({ ok: true, uuid: jobUuid });
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    console.error('[jobDelete] Error eliminando montaje:', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'Error interno al eliminar el montaje.' });
  } finally {
    client.release();
  }
}

module.exports = { deleteJob };
