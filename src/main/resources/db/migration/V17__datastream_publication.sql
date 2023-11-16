DO
$$
BEGIN
        if not exists
            (select 1 from pg_publication where pubname = 'bro_publication')
        then
            CREATE PUBLICATION bro_publication for ALL TABLES;
end if;
end;
$$;
