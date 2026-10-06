CREATE TABLE production_task_target (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    production_task_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    target_quantity BIGINT UNSIGNED NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_target_product (production_task_id, product_id),
    CONSTRAINT fk_task_target_task FOREIGN KEY (production_task_id) REFERENCES production_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_target_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE RESTRICT,
    CONSTRAINT chk_task_target_quantity CHECK (target_quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO production_task_target (production_task_id, product_id, target_quantity, sort_order)
SELECT id, product_id, target_quantity, 0 FROM production_task;
