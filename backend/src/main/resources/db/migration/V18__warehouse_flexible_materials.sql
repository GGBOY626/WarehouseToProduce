CREATE TABLE warehouse_photo (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    content MEDIUMBLOB NOT NULL,
    retained BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE warehouse_stock_item DROP FOREIGN KEY fk_stock_product;
ALTER TABLE warehouse_stock_item
    MODIFY COLUMN product_id BIGINT UNSIGNED NULL,
    MODIFY COLUMN material_code VARCHAR(100) NULL,
    ADD COLUMN raw_material_batch VARCHAR(100) NULL,
    ADD COLUMN photo_id BIGINT UNSIGNED NULL,
    ADD CONSTRAINT fk_stock_photo FOREIGN KEY(photo_id) REFERENCES warehouse_photo(id),
    ADD CONSTRAINT fk_stock_product FOREIGN KEY(product_id) REFERENCES product(id);

ALTER TABLE warehouse_area ADD COLUMN rack_row VARCHAR(1) NULL;
-- Keep existing area IDs and pallet locations. The initial aisle belongs beside row A.
UPDATE warehouse_area SET rack_row='A',lanes=4,version=version+1,updated_at=NOW(6)
WHERE kind='AISLE' AND name IN ('过道 1','A 排过道');
INSERT INTO warehouse_area (kind,name,lanes,rack_row,created_at,updated_at)
SELECT 'AISLE','A 排过道',4,'A',NOW(6),NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM warehouse_area WHERE rack_row='A' AND kind='AISLE');
