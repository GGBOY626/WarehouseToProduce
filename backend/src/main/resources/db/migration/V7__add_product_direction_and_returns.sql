ALTER TABLE product
    ADD COLUMN movement_direction VARCHAR(40) NULL AFTER material_batch,
    ADD KEY idx_product_movement_direction (movement_direction),
    ADD CONSTRAINT chk_product_movement_direction
        CHECK (movement_direction IS NULL OR movement_direction IN ('WAREHOUSE_TO_PRODUCTION','PRODUCTION_TO_WAREHOUSE'));

ALTER TABLE movement
    ADD COLUMN is_return BOOLEAN NOT NULL DEFAULT FALSE AFTER manufacture_lot,
    ADD KEY idx_movement_return_time (is_return, movement_time),
    ADD CONSTRAINT chk_movement_return_direction
        CHECK (is_return = FALSE OR direction = 'PRODUCTION_TO_WAREHOUSE');
