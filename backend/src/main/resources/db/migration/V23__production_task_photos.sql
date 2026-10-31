CREATE TABLE IF NOT EXISTS production_task_photo (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    production_task_id BIGINT UNSIGNED NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT UNSIGNED NOT NULL,
    width INT UNSIGNED NOT NULL,
    height INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_production_task_photo_task_created (production_task_id, created_at),
    CONSTRAINT fk_production_task_photo_task FOREIGN KEY (production_task_id) REFERENCES production_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
