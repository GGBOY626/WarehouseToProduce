CREATE TABLE production_recipe (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    output_product_id BIGINT UNSIGNED NOT NULL,
    primary_material_product_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_output_name (output_product_id, name),
    KEY idx_recipe_primary_material (primary_material_product_id),
    CONSTRAINT fk_recipe_output_product FOREIGN KEY (output_product_id) REFERENCES product(id) ON DELETE RESTRICT,
    CONSTRAINT fk_recipe_primary_material FOREIGN KEY (primary_material_product_id) REFERENCES product(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE production_recipe_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    recipe_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    quantity_per_output DECIMAL(18,6) NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_item_product (recipe_id, product_id),
    KEY idx_recipe_item_product (product_id),
    CONSTRAINT fk_recipe_item_recipe FOREIGN KEY (recipe_id) REFERENCES production_recipe(id) ON DELETE CASCADE,
    CONSTRAINT fk_recipe_item_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE RESTRICT,
    CONSTRAINT chk_recipe_item_quantity CHECK (quantity_per_output > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE production_task
    ADD COLUMN recipe_id BIGINT UNSIGNED NULL AFTER output_product_id,
    ADD COLUMN recipe_name_snapshot VARCHAR(100) NULL AFTER recipe_id,
    ADD CONSTRAINT fk_task_recipe FOREIGN KEY (recipe_id) REFERENCES production_recipe(id) ON DELETE RESTRICT;
