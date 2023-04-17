-- Legger til midlertidig defaultverdi for å utfylle databasen i dev, er ikke noe data i prod ennå
ALTER TABLE forespoersel ADD COLUMN skjaeringstidspunkt DATE NOT NULL DEFAULT DATE('2003-02-05');