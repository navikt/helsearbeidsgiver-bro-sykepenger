DROP INDEX idx_forespoersel_id;

CREATE INDEX idx_eksponert_forespoersel_id ON forespoersel (eksponert_forespoersel_id);
