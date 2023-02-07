CREATE TABLE forespoersel
(
    id                   BIGSERIAL   NOT NULL PRIMARY KEY,
    forespoersel_id      UUID        NOT NULL UNIQUE,
    fnr                  VARCHAR(50) NOT NULL,
    orgnr                VARCHAR(50) NOT NULL,
    vedtaksperiode_id    UUID        NOT NULL,
    sykmeldingsperioder  JSONB       NOT NULL,
    forespurt_data       JSONB       NOT NULL,
    status               VARCHAR(50) NOT NULL,
    forespoersel_besvart TIMESTAMP,
    opprettet            TIMESTAMP   NOT NULL DEFAULT now(),
    oppdatert            TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_forespoersel_id ON forespoersel(forespoersel_id);
CREATE INDEX idx_vedtaksperiode_id ON forespoersel(vedtaksperiode_id);
