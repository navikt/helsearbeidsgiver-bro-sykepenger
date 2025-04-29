ALTER TABLE forespoersel
    ADD COLUMN eksponert_forespoersel_id UUID REFERENCES forespoersel (forespoersel_id),
    ALTER COLUMN type TYPE TEXT,
    ALTER COLUMN status TYPE TEXT,
    ALTER COLUMN fnr TYPE TEXT,
    ALTER COLUMN orgnr TYPE TEXT;
