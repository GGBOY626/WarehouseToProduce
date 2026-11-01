ALTER TABLE record_original ADD COLUMN record_end_date DATE NULL AFTER record_date;
UPDATE record_original SET record_end_date=record_date WHERE record_end_date IS NULL;
ALTER TABLE record_original MODIFY COLUMN record_end_date DATE NOT NULL;
DROP INDEX idx_original_date_direction ON record_original;
CREATE INDEX idx_original_date_direction ON record_original (record_end_date, direction, record_date);
