ALTER TABLE movement_item_issue
    ADD COLUMN updated_at DATETIME(6) NULL AFTER created_at;

UPDATE movement_item_issue
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE movement_item_issue
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE movement_photo
    ADD COLUMN updated_at DATETIME(6) NULL AFTER created_at;

UPDATE movement_photo
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE movement_photo
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;
