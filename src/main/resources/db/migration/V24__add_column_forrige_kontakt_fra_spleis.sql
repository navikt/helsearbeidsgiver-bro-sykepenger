ALTER TABLE forespoersel
    ADD COLUMN forrige_kontakt_fra_spleis TIMESTAMP NOT NULL DEFAULT TIMESTAMP '2000-01-01 00:00:00.000000';

ALTER TABLE forespoersel
    ALTER COLUMN forrige_kontakt_fra_spleis SET DEFAULT now();
