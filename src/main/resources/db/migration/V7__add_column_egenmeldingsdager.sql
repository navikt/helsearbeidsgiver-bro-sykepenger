ALTER TABLE forespoersel ADD COLUMN egenmeldingsperioder JSONB NOT NULL DEFAULT '[]';
ALTER TABLE forespoersel ALTER COLUMN egenmeldingsperioder DROP DEFAULT;