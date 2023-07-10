CREATE TABLE besvarelse_metadata(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    fk_forespoersel_id BIGSERIAL REFERENCES forespoersel(id) ON DELETE CASCADE,
    forespoersel_besvart TIMESTAMP NOT NULL,
    inntektsmelding_id UUID,
    UNIQUE(fk_forespoersel_id)
)