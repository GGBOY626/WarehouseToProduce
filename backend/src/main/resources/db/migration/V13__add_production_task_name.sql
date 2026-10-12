-- Keep existing tasks unchanged; unnamed tasks use a short display fallback.
ALTER TABLE production_task ADD COLUMN name VARCHAR(100) NULL;
