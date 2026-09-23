-- Sitio profesional de Grupo Ricali: tienda de componentes sueltos +
-- galería de instalaciones terminadas. Mismo patrón que 005_catalog.sql.

-- catalog_products: componentes que se venden por separado en la tienda
-- física (paneles, inversores, baterías, estructuras, cableado, etc).
-- `category` es texto libre (no tabla aparte) a propósito: son pocas
-- categorías estables, no hace falta un catálogo de categorías editable.
CREATE TABLE catalog_products (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE,
  company_id BIGINT NOT NULL REFERENCES companies(id),
  category TEXT,
  name TEXT NOT NULL,
  price_usd NUMERIC(12,2),
  description TEXT,
  in_stock BOOLEAN NOT NULL DEFAULT true,
  active BOOLEAN NOT NULL DEFAULT true,
  sort_order INT NOT NULL DEFAULT 0,
  created_by_device_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_catalogproducts_company_updated ON catalog_products (company_id, updated_at);

CREATE TABLE catalog_product_photos (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE,
  company_id BIGINT NOT NULL REFERENCES companies(id),
  product_id BIGINT NOT NULL REFERENCES catalog_products(id),
  storage_url TEXT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  uploaded_by_user_id BIGINT NOT NULL REFERENCES users(id),
  created_by_device_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_catalogproductphotos_company_updated ON catalog_product_photos (company_id, updated_at);

-- gallery_photos: instalaciones terminadas, para la sección "Galería" del
-- sitio público. No tiene un "padre" como kit/producto — es una foto suelta
-- con descripción opcional.
CREATE TABLE gallery_photos (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL UNIQUE,
  company_id BIGINT NOT NULL REFERENCES companies(id),
  storage_url TEXT NOT NULL,
  caption TEXT,
  active BOOLEAN NOT NULL DEFAULT true,
  sort_order INT NOT NULL DEFAULT 0,
  uploaded_by_user_id BIGINT NOT NULL REFERENCES users(id),
  created_by_device_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_galleryphotos_company_updated ON gallery_photos (company_id, updated_at);
