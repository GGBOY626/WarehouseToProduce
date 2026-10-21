-- Keep legacy task batch values readable while allowing new tasks without one.
-- MySQL DDL is not transactional; additions can resume after an interrupted attempt.
ALTER TABLE production_task MODIFY COLUMN batch_no VARCHAR(100) NULL;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='production_task' AND column_name='order_no'), 'SELECT 1', 'ALTER TABLE production_task ADD COLUMN order_no VARCHAR(100) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='production_task' AND column_name='feishu_no'), 'SELECT 1', 'ALTER TABLE production_task ADD COLUMN feishu_no VARCHAR(100) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='production_task' AND column_name='planned_quantity'), 'SELECT 1', 'ALTER TABLE production_task ADD COLUMN planned_quantity BIGINT UNSIGNED NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='production_task' AND column_name='product_batch'), 'SELECT 1', 'ALTER TABLE production_task ADD COLUMN product_batch VARCHAR(100) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS product_production_number (
    product_id BIGINT UNSIGNED NOT NULL,
    production_number VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (product_id, production_number),
    CONSTRAINT fk_product_production_number_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='movement_item' AND column_name='production_number'), 'SELECT 1', 'ALTER TABLE movement_item ADD COLUMN production_number VARCHAR(100) NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
