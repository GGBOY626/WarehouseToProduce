-- Existing loose quantities remain loose. No historical totals or products are rewritten.
ALTER TABLE movement_item
    ADD COLUMN tail_cartons INT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN tail_units_per_carton BIGINT UNSIGNED NULL,
    DROP CHECK chk_item_has_quantity,
    ADD CONSTRAINT chk_item_has_quantity CHECK (
        quantity_unknown = TRUE OR full_cartons > 0 OR tail_cartons > 0 OR loose_units > 0 OR total_units > 0
    ),
    ADD CONSTRAINT chk_item_tail_quantity CHECK (
        (tail_cartons = 0 AND tail_units_per_carton IS NULL) OR
        (tail_cartons > 0 AND tail_units_per_carton IS NOT NULL AND tail_units_per_carton > 0)
    );

CREATE TABLE product_carton_spec (
    product_id BIGINT UNSIGNED NOT NULL,
    units_per_carton INT UNSIGNED NOT NULL,
    PRIMARY KEY (product_id, units_per_carton),
    CONSTRAINT fk_carton_spec_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    CONSTRAINT chk_carton_spec_positive CHECK (units_per_carton > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
