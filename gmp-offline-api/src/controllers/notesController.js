const pool = require('../db/pool');

async function listNotes(req, res) {
  try {
    const result = await pool.query(
      `SELECT uuid, type, title, body, items, created_at, updated_at
       FROM notes
       WHERE company_id = $1 AND user_id = $2 AND deleted_at IS NULL
       ORDER BY updated_at DESC, id DESC`,
      [req.user.company_id, req.user.user_id]
    );
    return res.status(200).json(result.rows);
  } catch (err) {
    console.error('[notes] list error', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'No se pudieron cargar los apuntes.' });
  }
}

async function upsertNote(req, res) {
  const { uuid } = req.params;
  const { type, title = '', body = '', items = [] } = req.body || {};
  if (!['text', 'checklist'].includes(type)) {
    return res.status(400).json({ error_code: 'invalid_note_type', message: 'Tipo de apunte inválido.' });
  }
  try {
    const result = await pool.query(
      `INSERT INTO notes (uuid, company_id, user_id, type, title, body, items)
       VALUES ($1, $2, $3, $4, $5, $6, $7::jsonb)
       ON CONFLICT (uuid) DO UPDATE SET
         type = EXCLUDED.type,
         title = EXCLUDED.title,
         body = EXCLUDED.body,
         items = EXCLUDED.items,
         updated_at = NOW(),
         deleted_at = NULL
       WHERE notes.company_id = EXCLUDED.company_id AND notes.user_id = EXCLUDED.user_id
       RETURNING uuid, type, title, body, items, created_at, updated_at`,
      [uuid, req.user.company_id, req.user.user_id, type, title, body, JSON.stringify(items)]
    );
    if (!result.rows.length) return res.status(403).json({ error_code: 'forbidden', message: 'No puedes modificar este apunte.' });
    return res.status(200).json(result.rows[0]);
  } catch (err) {
    console.error('[notes] upsert error', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'No se pudo guardar el apunte.' });
  }
}

async function deleteNote(req, res) {
  try {
    await pool.query(
      `UPDATE notes SET deleted_at = NOW(), updated_at = NOW()
       WHERE uuid = $1 AND company_id = $2 AND user_id = $3 AND deleted_at IS NULL`,
      [req.params.uuid, req.user.company_id, req.user.user_id]
    );
    return res.status(200).json({ ok: true });
  } catch (err) {
    console.error('[notes] delete error', err);
    return res.status(500).json({ error_code: 'internal_error', message: 'No se pudo eliminar el apunte.' });
  }
}

module.exports = { listNotes, upsertNote, deleteNote };
