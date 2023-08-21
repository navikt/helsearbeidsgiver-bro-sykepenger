DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 FROM pg_user where usename = 'bro_naisjobb_bruker')
        THEN
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "bro_naisjobb_bruker";
        END IF;
    END
$$;
