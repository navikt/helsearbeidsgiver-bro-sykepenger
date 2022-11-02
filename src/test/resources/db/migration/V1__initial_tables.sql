CREATE TABLE forespoersel(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    fnr VARCHAR(50) NOT NULL,
    orgnr VARCHAR(50) NOT NULL,
    vedtaksperiode_id UUID NOT NULL,
    fom DATE NOT NULL,
    tom DATE NOT NULL,
    forespurt_data JSONB NOT NULL,
    forespoersel_besvart TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    opprettet TIMESTAMP NOT NULL,
    oppdatert TIMESTAMP NOT NULL
);