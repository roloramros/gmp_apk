-- Fase 2: soporte para super-admin

ALTER TABLE companies ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE TABLE super_admins (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  full_name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE billing_reports (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  company_id BIGINT REFERENCES companies(id),
  period_from TIMESTAMPTZ NOT NULL,
  period_to TIMESTAMPTZ NOT NULL,
  generated_by_super_admin_id BIGINT NOT NULL REFERENCES super_admins(id),
  report_data JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_billingreports_company ON billing_reports (company_id);
