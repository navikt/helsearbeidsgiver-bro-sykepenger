CREATE TABLE status
(
    id                BIGSERIAL PRIMARY KEY,
    forespoersel_id   UUID UNIQUE NOT NULL,
    vedtaksperiode_id UUID        NOT NULL,
    status            TEXT        NOT NULL,
    opprettet         TIMESTAMP   NOT NULL DEFAULT now()
);
