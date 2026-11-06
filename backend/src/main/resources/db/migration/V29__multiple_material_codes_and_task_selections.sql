CREATE TABLE IF NOT EXISTS product_material_code (
    product_id BIGINT UNSIGNED NOT NULL,
    material_code VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (product_id, material_code),
    CONSTRAINT fk_product_material_code_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO product_material_code(product_id, material_code)
SELECT id, material_code FROM product WHERE material_code IS NOT NULL AND TRIM(material_code) <> '';

CREATE TABLE IF NOT EXISTS production_task_target_material_code (
    target_id BIGINT UNSIGNED NOT NULL,
    material_code VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (target_id, material_code),
    CONSTRAINT fk_task_target_material_code_target FOREIGN KEY (target_id) REFERENCES production_task_target(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS production_task_target_production_number (
    target_id BIGINT UNSIGNED NOT NULL,
    production_number VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (target_id, production_number),
    CONSTRAINT fk_task_target_production_number_target FOREIGN KEY (target_id) REFERENCES production_task_target(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS production_task_target_material_batch (
    target_id BIGINT UNSIGNED NOT NULL,
    batch_no VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (target_id, batch_no),
    CONSTRAINT fk_task_target_material_batch_target FOREIGN KEY (target_id) REFERENCES production_task_target(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO production_task_target_material_batch(target_id, batch_no)
SELECT id, material_batch FROM production_task_target WHERE material_batch IS NOT NULL AND TRIM(material_batch) <> '';
