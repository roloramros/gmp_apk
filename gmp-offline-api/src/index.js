require('dotenv').config();
const express = require('express');
const cors = require('cors');
const pool = require('./db/pool');

const app = express();
app.use(cors());
app.use(express.json());

const authRoutes = require('./routes/auth');
app.use('/', authRoutes);
const staffRoutes = require('./routes/staff');
app.use('/', staffRoutes);
const superAdminRoutes = require('./routes/superAdmin');
app.use('/', superAdminRoutes);
const materialsRoutes = require('./routes/materials');
app.use('/materials', materialsRoutes);
const jobsRoutes = require('./routes/jobs');
app.use('/jobs', jobsRoutes);
const syncRoutes = require('./routes/sync');
app.use('/sync', syncRoutes);
const deviceTokensRoutes = require('./routes/deviceTokens');
app.use('/device-tokens', deviceTokensRoutes);

app.get('/health', async (req, res) => {
  try {
    const result = await pool.query('SELECT NOW()');
    res.json({ status: 'ok', db_time: result.rows[0].now });
  } catch (err) {
    console.error(err);
    res.status(500).json({ status: 'error', message: 'DB connection failed' });
  }
});

const PORT = process.env.PORT || 3002;
app.listen(PORT, () => {
  console.log(`GMP Offline API listening on port ${PORT}`);
});
