ALTER TABLE forespoersel ADD COLUMN type VARCHAR NOT NULL DEFAULT 'KOMPLETT';
ALTER TABLE forespoersel ALTER COLUMN type DROP DEFAULT;