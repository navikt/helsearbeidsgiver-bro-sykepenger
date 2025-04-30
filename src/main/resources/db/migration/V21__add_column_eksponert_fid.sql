ALTER TABLE forespoersel
    ADD COLUMN eksponert_forespoersel_id UUID REFERENCES forespoersel (forespoersel_id) ON DELETE RESTRICT,
    ALTER COLUMN type TYPE TEXT,
    ALTER COLUMN status TYPE TEXT,
    ALTER COLUMN fnr TYPE VARCHAR(11),
    ALTER COLUMN orgnr TYPE VARCHAR(9);
