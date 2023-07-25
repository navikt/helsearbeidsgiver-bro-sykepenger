CREATE TRIGGER forespoersel_autoupdate_column_oppdatert
    BEFORE UPDATE
    ON forespoersel
    FOR EACH ROW
EXECUTE FUNCTION autoupdate_column_oppdatert();
