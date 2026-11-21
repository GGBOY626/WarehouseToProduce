ALTER TABLE product
    ADD COLUMN material_category VARCHAR(20) NOT NULL DEFAULT 'OTHER' AFTER movement_direction,
    ADD CONSTRAINT chk_product_material_category
        CHECK (material_category IN ('PRIMARY', 'OUTER_BOX', 'LINER', 'INNER_BOX', 'OTHER'));
