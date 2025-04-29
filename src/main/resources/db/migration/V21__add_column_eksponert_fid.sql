ALTER TABLE forespoersel
    ADD COLUMN eksponert_forespoersel_id UUID REFERENCES forespoersel (forespoersel_id) ON DELETE RESTRICT,
    ALTER COLUMN type TYPE TEXT,
    ALTER COLUMN status TYPE TEXT,
    ALTER COLUMN fnr TYPE TEXT,
    ALTER COLUMN orgnr TYPE TEXT;
