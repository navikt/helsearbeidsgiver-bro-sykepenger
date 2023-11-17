DO
$$
BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'helsearbeidsgiver-bro-sykepenger')
        THEN
            ALTER USER "helsearbeidsgiver-bro-sykepenger" WITH REPLICATION;
END IF;
END
$$;
DO
$$
BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'bro_datastream_bruker')
        THEN
            ALTER USER "bro_datastream_bruker" WITH REPLICATION;
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO "bro_datastream_bruker";
GRANT USAGE ON SCHEMA public TO "bro_datastream_bruker";
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "bro_datastream_bruker";
END IF;
END
$$;
