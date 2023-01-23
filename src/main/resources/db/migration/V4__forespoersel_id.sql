ALTER TABLE forespoersel
    ADD COLUMN forespoersel_id UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE forespoersel
    ALTER COLUMN forespoersel_id DROP DEFAULT;