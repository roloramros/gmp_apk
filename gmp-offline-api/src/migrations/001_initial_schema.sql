-- Fase 2: esquema inicial completo (basado en diseño de Fase 1)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- companies
CREATE TABLE companies (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'trial',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- users (staff + clientes)
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  company_id BIGINT NOT NULL REFERENCES companies(id),
  phone TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL,
  full_name TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  created_by_device_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  UNIQUE (company_id, phone)
);
CREATE INDEX idx_users_company_updated ON users (company_id, updated_at);

-- jobs
CREATE TABLE jobs (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE,
  company_id BIGINT NOT NULL REFERENCES companies(id),
  client_id BIGINT REFERENCES users(id),
  created_by_user_id BIGINT NOT NULL REFERENCES users(id),
  title TEXT NOT NULL,
  description TEXT,
  status TEXT NOT NULL DEFAULT 'pending',
  address TEXT,
  scheduled_at TIMESTAMPTZ,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  invoiced_at TIMESTAMPTZ,
  total_amount NUMERIC(12,2),
  amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0,
  cancelled_at TIMESTAMPTZ,
  created_by_device_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_jobs_company_updated ON jobs (company_id, updated_at);

-- job_workers
CREATE TABLE job_workers (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE,
  company_id BIGINT NOT NULL REFERENCES companies(id),
  job_id BIGINT NOT NULL REFERENCES jobs(id),
  user_id BIGINT NOT NULL REFERENCES users(id),
  created_by_device_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  UNIQUE (job_id, user_id)
);
CREATE INDEX idx_jobworkers_company_updated ON job_workers (company_id, updated_at);

-- materials (catálogo)
CREATE TABLE materials (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE,
  company_id BIGINT NOT NULL REFERENCES companies(id),
  name TEXT NOT NULL,
  unit TEXT,
  default_price NUMERIC(12,2),
  created_by_device_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_materials_company_updated ON materials (company_id, updated_at);

-- job_materials
CREATE TABLE job_materials (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE,
  company_id BIGINT NOT NULL REFERENCES companies(id),
  job_id BIGINT NOT NULL REFERENCES jobs(id),
  material_id BIGINT REFERENCES materials(id),
  free_text_description TEXT,
  quantity NUMERIC(12,2) NOT NULL,
  unit_price NUMERIC(12,2),
  created_by_device_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_jobmaterials_company_updated ON job_materials (company_id, updated_at);

-- job_photos
CREATE TABLE job_photos (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE,
  company_id BIGINT NOT NULL REFERENCES companies(id),
  job_id BIGINT NOT NULL REFERENCES jobs(id),
  storage_url TEXT NOT NULL,
  uploaded_by_user_id BIGINT NOT NULL REFERENCES users(id),
  created_by_device_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_jobphotos_company_updated ON job_photos (company_id, updated_at);

-- command_log (idempotencia)
CREATE TABLE command_log (
  id BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL REFERENCES companies(id),
  user_id BIGINT NOT NULL REFERENCES users(id),
  command_uuid UUID NOT NULL UNIQUE,
  endpoint TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  result_status INT NOT NULL,
  result_body JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_commandlog_company_created ON command_log (company_id, created_at);

-- device_tokens (preparación FCM)
CREATE TABLE device_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  fcm_token TEXT NOT NULL UNIQUE,
  platform TEXT NOT NULL DEFAULT 'android',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
