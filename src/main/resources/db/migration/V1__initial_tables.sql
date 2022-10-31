CREATE TABLE forespoersel(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    fnr VARCHAR(50) NOT NULL,
    organisasjonsnummer VARCHAR(50) NOT NULL,
    vedtaksperiodeId UUID NOT NULL,
    vedtaksperiodeFom DATE NOT NULL,
    vedtaksperiodeTom DATE NOT NULL,
    behov JSONB NOT NULL,
    status VARCHAR(50),
    forespoerselBesvart TIMESTAMP,
    opprettet TIMESTAMP NOT NULL,
    oppdatert TIMESTAMP NOT NULL
);