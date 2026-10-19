ALTER TABLE warehouse_area MODIFY COLUMN rack_row VARCHAR(2) NULL;

UPDATE warehouse_area
SET name=CONCAT(name,name), version=version+1, updated_at=NOW(6)
WHERE kind='RACK' AND name REGEXP '^[A-M]$';

UPDATE warehouse_area
SET rack_row=CONCAT(rack_row,rack_row), version=version+1, updated_at=NOW(6)
WHERE kind='AISLE' AND rack_row REGEXP '^[A-M]$';

ALTER TABLE warehouse_stock_item
    ADD COLUMN units_per_carton INT NULL,
    ADD COLUMN boxes_per_carton INT NULL,
    ADD COLUMN units_per_box INT NULL,
    ADD COLUMN finished BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE movement ADD COLUMN finished BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE movement SET finished=(direction='PRODUCTION_TO_WAREHOUSE' AND is_return=FALSE);

-- Existing numbered rack slots 01-12 correspond to the physical 05-16 labels.
ALTER TABLE warehouse_pallet DROP INDEX uk_pallet_position;
UPDATE warehouse_pallet p JOIN warehouse_area a ON a.id=p.area_id
SET p.position_no=p.position_no+4
WHERE a.kind='RACK' AND p.position_no BETWEEN 1 AND 12;
ALTER TABLE warehouse_pallet ADD CONSTRAINT uk_pallet_position UNIQUE(area_id,lane,position_no);
