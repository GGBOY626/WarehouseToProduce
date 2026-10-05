CREATE TABLE record_original (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    record_date DATE NOT NULL,
    direction VARCHAR(40) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_original_date_direction (record_date, direction)
);
