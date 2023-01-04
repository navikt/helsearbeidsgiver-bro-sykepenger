ALTER TABLE forespoersel
    DROP COLUMN fom,
    DROP COLUMN tom,
    ADD COLUMN sykmeldingsperioder JSONB NOT NULL;