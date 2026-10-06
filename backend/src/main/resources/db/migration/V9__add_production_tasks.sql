CREATE TABLE production_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id BIGINT UNSIGNED NOT NULL,
    target_quantity BIGINT UNSIGNED NOT NULL,
    planned_date DATE NOT NULL,
    batch_no VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PREPARING',
    remarks VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_production_task_status_date (status, planned_date),
    KEY idx_production_task_product (product_id),
    CONSTRAINT fk_production_task_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE RESTRICT,
    CONSTRAINT chk_production_task_target CHECK (target_quantity > 0),
    CONSTRAINT chk_production_task_status CHECK (status IN ('PREPARING','IN_PROGRESS','COMPLETED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE movement ADD COLUMN production_task_id BIGINT UNSIGNED NULL AFTER is_return;
ALTER TABLE movement ADD KEY idx_movement_production_task (production_task_id);
ALTER TABLE movement ADD CONSTRAINT fk_movement_production_task FOREIGN KEY (production_task_id) REFERENCES production_task(id) ON DELETE SET NULL;
