ALTER TABLE forespoersel
    DROP COLUMN skjaeringstidspunkt,
    ALTER COLUMN eksponert_forespoersel_id SET NOT NULL;
