-- Preserve source products and historical foreign keys; merged products become aliases.
ALTER TABLE product ADD COLUMN merged_into_id BIGINT UNSIGNED NULL,
    ADD CONSTRAINT fk_product_merged_into FOREIGN KEY (merged_into_id) REFERENCES product(id);
CREATE TABLE product_material_batch (
    product_id BIGINT UNSIGNED NOT NULL,
    batch_no VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (product_id, batch_no),
    CONSTRAINT fk_product_batch_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE movement_item ADD COLUMN material_batch VARCHAR(100) NULL;
UPDATE movement_item i JOIN movement m ON m.id=i.movement_id
    SET i.material_batch=COALESCE(m.manufacture_lot,'');
ALTER TABLE production_task_target
    DROP INDEX uk_task_target_product,
    ADD COLUMN material_batch VARCHAR(100) COLLATE utf8mb4_bin NOT NULL DEFAULT '',
    ADD UNIQUE KEY uk_task_target_product_batch (production_task_id,product_id,material_batch);
ALTER TABLE production_task ADD COLUMN output_material_batch VARCHAR(100) NULL;
-- Existing tasks had no batch constraint: keep their original all-batches meaning.

CREATE TEMPORARY TABLE product_merge_map AS
SELECT id, FIRST_VALUE(id) OVER (
    PARTITION BY BINARY TRIM(name), BINARY TRIM(material_code), movement_direction,
                 BINARY COALESCE(base_unit,''), quantity_unknown
    ORDER BY active DESC,id
) AS canonical_id
FROM product;

INSERT IGNORE INTO product_material_batch (product_id,batch_no)
SELECT m.canonical_id,TRIM(p.material_batch) FROM product p JOIN product_merge_map m ON m.id=p.id
WHERE p.material_batch IS NOT NULL AND TRIM(p.material_batch)<>'';
INSERT IGNORE INTO product_material_batch (product_id,batch_no)
SELECT m.canonical_id,TRIM(i.material_batch) FROM movement_item i JOIN product_merge_map m ON m.id=i.product_id
WHERE TRIM(i.material_batch)<>'';
INSERT IGNORE INTO product_carton_spec (product_id,units_per_carton)
SELECT m.canonical_id,s.units_per_carton FROM product_carton_spec s JOIN product_merge_map m ON m.id=s.product_id;
INSERT IGNORE INTO product_carton_spec (product_id,units_per_carton)
SELECT m.canonical_id,p.default_units_per_carton FROM product p JOIN product_merge_map m ON m.id=p.id
WHERE p.default_units_per_carton>0;

UPDATE product p JOIN product_merge_map m ON m.id=p.id
SET p.merged_into_id=m.canonical_id,p.active=0 WHERE m.id<>m.canonical_id;
DROP TEMPORARY TABLE product_merge_map;
ALTER TABLE product DROP INDEX uk_product_active_identity, DROP COLUMN active_identity,
    ADD COLUMN active_identity VARCHAR(344) GENERATED ALWAYS AS (
        CASE WHEN active=1 THEN CONCAT(name,'::',material_code,'::',COALESCE(movement_direction,'')) ELSE NULL END
    ) STORED,
    ADD UNIQUE KEY uk_product_active_identity (active_identity);
