CREATE TABLE product_box_packing (
    product_id BIGINT UNSIGNED NOT NULL,
    boxes_per_carton INT NOT NULL,
    units_per_box INT NOT NULL,
    PRIMARY KEY (product_id, boxes_per_carton, units_per_box),
    CONSTRAINT fk_box_packing_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    CONSTRAINT chk_box_packing_positive CHECK (boxes_per_carton > 0 AND units_per_box > 0)
);
-- Totals alone cannot reliably identify the original box structure.
-- Existing product presets and movement quantities deliberately remain unchanged.
ALTER TABLE movement_item ADD COLUMN boxes_per_carton_snapshot INT NULL;
ALTER TABLE movement_item ADD COLUMN units_per_box_snapshot INT NULL;
