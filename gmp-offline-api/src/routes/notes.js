const express = require('express');
const router = express.Router();
const auth = require('../middleware/auth');
const { upsertNote, deleteNote } = require('../controllers/notesController');

router.put('/:uuid', auth, upsertNote);
router.delete('/:uuid', auth, deleteNote);

module.exports = router;
