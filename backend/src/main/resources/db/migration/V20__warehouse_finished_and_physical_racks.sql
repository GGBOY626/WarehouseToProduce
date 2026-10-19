ALTER TABLE warehouse_area MODIFY COLUMN rack_row VARCHAR(2) NULL;

UPDATE warehouse_area
SET name=CONCAT(name,name), version=version+1, updated_at=NOW(6)
WHERE kind='RACK' AND name REGEXP '^[A-M]$';

UPDATE warehouse_area
SET rack_row=CONCAT(rack_row,rack_row), version=version+1, updated_at=NOW(6)
WHERE kind='AISLE' AND rack_row REGEXP '^[A-M]$';

-- MySQL DDL is not rolled back with a failed Flyway migration. Each addition
-- also works when an earlier attempt stopped after creating the column.
SET @v20_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='warehouse_stock_item' AND column_name='units_per_carton')=0,
    'ALTER TABLE warehouse_stock_item ADD COLUMN units_per_carton INT NULL', 'DO 0');
PREPARE v20_stmt FROM @v20_sql;
EXECUTE v20_stmt;
DEALLOCATE PREPARE v20_stmt;

SET @v20_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='warehouse_stock_item' AND column_name='boxes_per_carton')=0,
    'ALTER TABLE warehouse_stock_item ADD COLUMN boxes_per_carton INT NULL', 'DO 0');
PREPARE v20_stmt FROM @v20_sql;
EXECUTE v20_stmt;
DEALLOCATE PREPARE v20_stmt;

SET @v20_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='warehouse_stock_item' AND column_name='units_per_box')=0,
    'ALTER TABLE warehouse_stock_item ADD COLUMN units_per_box INT NULL', 'DO 0');
PREPARE v20_stmt FROM @v20_sql;
EXECUTE v20_stmt;
DEALLOCATE PREPARE v20_stmt;

SET @v20_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='warehouse_stock_item' AND column_name='finished')=0,
    'ALTER TABLE warehouse_stock_item ADD COLUMN finished BOOLEAN NOT NULL DEFAULT FALSE', 'DO 0');
PREPARE v20_stmt FROM @v20_sql;
EXECUTE v20_stmt;
DEALLOCATE PREPARE v20_stmt;

SET @v20_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movement' AND column_name='finished')=0,
    'ALTER TABLE movement ADD COLUMN finished BOOLEAN NOT NULL DEFAULT FALSE', 'DO 0');
PREPARE v20_stmt FROM @v20_sql;
EXECUTE v20_stmt;
DEALLOCATE PREPARE v20_stmt;

UPDATE movement SET finished=(direction='PRODUCTION_TO_WAREHOUSE' AND is_return=FALSE);

-- Existing numbered rack slots 01-12 correspond to the physical 05-16 labels.
-- Descending order avoids temporary collisions with the existing unique key.
-- Keeping that key also keeps the fk_pallet_area child-side index available.
UPDATE warehouse_pallet
SET position_no=position_no+4
WHERE area_id IN (SELECT id FROM warehouse_area WHERE kind='RACK')
  AND position_no BETWEEN 1 AND 12
ORDER BY area_id, lane, position_no DESC;
