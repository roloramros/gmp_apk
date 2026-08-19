const express = require('express');
const router = express.Router();
const auth = require('../middleware/auth');
const { listNotes, upsertNote, deleteNote } = require('../controllers/notesController');

router.get('/', auth, listNotes);
router.put('/:uuid', auth, upsertNote);
router.delete('/:uuid', auth, deleteNote);

module.exports = router;
