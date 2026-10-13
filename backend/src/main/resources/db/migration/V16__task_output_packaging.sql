-- Optional task-level packing and primary material. Existing quantities remain unchanged.
ALTER TABLE production_task
    ADD COLUMN output_boxes_per_carton INT UNSIGNED NULL,
    ADD COLUMN output_units_per_box INT UNSIGNED NULL,
    ADD COLUMN output_quantity_basis VARCHAR(10) NULL,
    ADD COLUMN primary_material_product_id BIGINT UNSIGNED NULL,
    ADD CONSTRAINT fk_task_primary_material FOREIGN KEY (primary_material_product_id) REFERENCES product(id),
    ADD CONSTRAINT chk_task_output_packaging CHECK (
        (output_boxes_per_carton IS NULL AND output_units_per_box IS NULL AND output_quantity_basis IS NULL AND primary_material_product_id IS NULL)
        OR (output_product_id IS NOT NULL AND output_boxes_per_carton IS NOT NULL AND output_units_per_box IS NOT NULL AND output_quantity_basis IS NOT NULL AND output_boxes_per_carton > 0 AND output_units_per_box > 0
            AND output_quantity_basis IN ('BOX','UNIT') AND primary_material_product_id IS NOT NULL)
    );
