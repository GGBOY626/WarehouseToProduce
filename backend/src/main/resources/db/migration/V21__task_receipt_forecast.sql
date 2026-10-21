-- Additive and restart-safe for MySQL DDL partial execution. No historical values are inferred.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_box_packing' AND column_name='cartons_per_pallet'), 'SELECT 1', 'ALTER TABLE product_box_packing ADD COLUMN cartons_per_pallet INT NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='production_task' AND column_name='output_cartons_per_pallet'), 'SELECT 1', 'ALTER TABLE production_task ADD COLUMN output_cartons_per_pallet INT NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='production_task' AND column_name='yield_material_quantity'), 'SELECT 1', 'ALTER TABLE production_task ADD COLUMN yield_material_quantity BIGINT NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='production_task' AND column_name='yield_output_quantity'), 'SELECT 1', 'ALTER TABLE production_task ADD COLUMN yield_output_quantity BIGINT NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
