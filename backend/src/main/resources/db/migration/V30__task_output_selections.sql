CREATE TABLE production_task_output_material_batch (
    task_id BIGINT UNSIGNED NOT NULL,
    batch_no VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (task_id, batch_no),
    CONSTRAINT fk_task_output_material_batch FOREIGN KEY (task_id) REFERENCES production_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE production_task_output_material_code (
    task_id BIGINT UNSIGNED NOT NULL,
    material_code VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (task_id, material_code),
    CONSTRAINT fk_task_output_material_code FOREIGN KEY (task_id) REFERENCES production_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE production_task_output_production_number (
    task_id BIGINT UNSIGNED NOT NULL,
    production_number VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (task_id, production_number),
    CONSTRAINT fk_task_output_production_number FOREIGN KEY (task_id) REFERENCES production_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
