-- Optional output plan: existing tasks and movement quantities remain unchanged.
ALTER TABLE production_task
    ADD COLUMN output_product_id BIGINT UNSIGNED NULL,
    ADD COLUMN target_output_quantity BIGINT UNSIGNED NULL,
    ADD CONSTRAINT fk_task_output_product FOREIGN KEY (output_product_id) REFERENCES product(id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_task_output_plan CHECK (
        (output_product_id IS NULL AND target_output_quantity IS NULL) OR
        (output_product_id IS NOT NULL AND target_output_quantity IS NOT NULL AND target_output_quantity > 0)
    );
