ALTER TABLE product
    DROP INDEX uk_product_sku,
    DROP COLUMN sku,
    ADD COLUMN material_batch VARCHAR(100) NULL AFTER material_code;
