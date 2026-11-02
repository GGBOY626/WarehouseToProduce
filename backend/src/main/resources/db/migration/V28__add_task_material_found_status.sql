ALTER TABLE production_task_target
    ADD COLUMN found BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN found_location VARCHAR(255) NULL;
