-- Tombstones técnicos para propagar hard deletes sin conservar el montaje ni sus relaciones.
CREATE TABLE IF NOT EXISTS sync_deletions (
  id BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL REFERENCES companies(id),
  entity_name TEXT NOT NULL,
  entity_uuid UUID NOT NULL,
  deleted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (company_id, entity_name, entity_uuid)
);

CREATE INDEX IF NOT EXISTS idx_sync_deletions_company_deleted
  ON sync_deletions (company_id, deleted_at);
