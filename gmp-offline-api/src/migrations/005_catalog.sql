-- Fase "Catálogo de kits": catálogo de ofertas configurable por empresa,
-- expuesto en una página pública estática (GET /public/catalog/:slug).

-- Perfil público de la empresa: slug para la URL (manual, no autogenerado)
-- + datos de contacto mostrados en la página del catálogo.
ALTER TABLE companies ADD COLUMN slug TEXT UNIQUE;
ALTER TABLE companies ADD COLUMN contact_whatsapp TEXT;
ALTER TABLE companies ADD COLUMN contact_facebook TEXT;
ALTER TABLE companies ADD COLUMN contact_instagram TEXT;

-- catalog_kits: los kits/ofertas configurables desde la app (solo admin).
-- Mismo patrón de sync que el resto: uuid generado por cliente, soft delete,
-- updated_at, created_by_device_id, índice (company_id, updated_at).
CREATE TABLE catalog_kits (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE,
  company_id BIGINT NOT NULL REFERENCES companies(id),
  name TEXT NOT NULL,
  power_kw NUMERIC(6,2),
  voltage TEXT,
  battery_kwh NUMERIC(6,2),
  panels_count INT,
  price_usd NUMERIC(12,2),
  description TEXT,
  active BOOLEAN NOT NULL DEFAULT true,
  sort_order INT NOT NULL DEFAULT 0,
  created_by_device_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_catalogkits_company_updated ON catalog_kits (company_id, updated_at);

-- catalog_kit_photos: mismo patrón que job_photos (storage local en disco).
CREATE TABLE catalog_kit_photos (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE,
  company_id BIGINT NOT NULL REFERENCES companies(id),
  kit_id BIGINT NOT NULL REFERENCES catalog_kits(id),
  storage_url TEXT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  uploaded_by_user_id BIGINT NOT NULL REFERENCES users(id),
  created_by_device_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_catalogkitphotos_company_updated ON catalog_kit_photos (company_id, updated_at);
