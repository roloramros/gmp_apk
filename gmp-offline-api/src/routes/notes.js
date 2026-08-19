const express = require('express');
const router = express.Router();
const { authenticate } = require('../middleware/auth');
const { listNotes, upsertNote, deleteNote } = require('../controllers/notesController');

router.get('/', authenticate, listNotes);
router.put('/:uuid', authenticate, upsertNote);
router.delete('/:uuid', authenticate, deleteNote);

module.exports = router;
