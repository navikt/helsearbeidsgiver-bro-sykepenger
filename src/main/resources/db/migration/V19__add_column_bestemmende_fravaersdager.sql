ALTER TABLE forespoersel
    ADD COLUMN bestemmende_fravaersdager JSONB NOT NULL DEFAULT '{}';

ALTER TABLE forespoersel
    ALTER COLUMN bestemmende_fravaersdager DROP DEFAULT;
