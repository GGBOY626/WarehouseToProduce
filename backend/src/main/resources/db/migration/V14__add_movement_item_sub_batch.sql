-- Optional physical sub-batch; no historical quantities or remarks are converted.
ALTER TABLE movement_item ADD COLUMN sub_batch VARCHAR(100) NULL;
