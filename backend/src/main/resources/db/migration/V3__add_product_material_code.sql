ALTER TABLE product
    ADD COLUMN material_code VARCHAR(100) NULL AFTER name;

UPDATE product
SET material_code = COALESCE(NULLIF(sku, ''), CONCAT('待补充-', id));

ALTER TABLE product
    MODIFY COLUMN material_code VARCHAR(100) NOT NULL,
    DROP INDEX uk_product_active_name,
    DROP COLUMN active_name,
    ADD COLUMN active_identity VARCHAR(302)
        GENERATED ALWAYS AS (
            CASE WHEN active = 1 THEN CONCAT(name, '::', material_code) ELSE NULL END
        ) STORED,
    ADD UNIQUE KEY uk_product_active_identity (active_identity),
    ADD KEY idx_product_material_code (material_code);
