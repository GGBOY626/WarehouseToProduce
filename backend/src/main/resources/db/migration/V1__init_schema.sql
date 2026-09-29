CREATE TABLE product (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    sku VARCHAR(100) NULL,
    default_units_per_carton INT UNSIGNED NULL,
    base_unit VARCHAR(20) NOT NULL DEFAULT '个',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    active_name VARCHAR(200) GENERATED ALWAYS AS (CASE WHEN active = 1 THEN name ELSE NULL END) STORED,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku (sku),
    UNIQUE KEY uk_product_active_name (active_name),
    KEY idx_product_active_name (active, name),
    CONSTRAINT chk_product_carton_size CHECK (default_units_per_carton IS NULL OR default_units_per_carton > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE person (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    remarks VARCHAR(500) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_person_name (name),
    KEY idx_person_active_name (active, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE movement_daily_sequence (
    sequence_date DATE NOT NULL,
    next_value INT UNSIGNED NOT NULL,
    PRIMARY KEY (sequence_date)
) ENGINE=InnoDB;

CREATE TABLE movement (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    record_no VARCHAR(20) NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    direction VARCHAR(40) NOT NULL,
    movement_time DATETIME(6) NOT NULL,
    sender_person_id BIGINT UNSIGNED NOT NULL,
    sender_name_snapshot VARCHAR(100) NOT NULL,
    receiver_person_id BIGINT UNSIGNED NOT NULL,
    receiver_name_snapshot VARCHAR(100) NOT NULL,
    manufacture_lot VARCHAR(100) NULL,
    total_cartons INT UNSIGNED NOT NULL DEFAULT 0,
    remarks VARCHAR(2000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    void_reason VARCHAR(500) NULL,
    voided_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_movement_record_no (record_no),
    UNIQUE KEY uk_movement_idempotency_key (idempotency_key),
    KEY idx_movement_time (movement_time),
    KEY idx_movement_status_time (status, movement_time),
    KEY idx_movement_direction_time (direction, movement_time),
    KEY idx_movement_manufacture_lot (manufacture_lot),
    KEY idx_movement_sender (sender_person_id),
    KEY idx_movement_receiver (receiver_person_id),
    CONSTRAINT fk_movement_sender FOREIGN KEY (sender_person_id) REFERENCES person(id) ON DELETE RESTRICT,
    CONSTRAINT fk_movement_receiver FOREIGN KEY (receiver_person_id) REFERENCES person(id) ON DELETE RESTRICT,
    CONSTRAINT chk_movement_direction CHECK (direction IN ('WAREHOUSE_TO_PRODUCTION','PRODUCTION_TO_WAREHOUSE')),
    CONSTRAINT chk_movement_status CHECK (status IN ('ACTIVE','VOID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE movement_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    movement_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    sku_snapshot VARCHAR(100) NULL,
    units_per_carton_snapshot INT UNSIGNED NULL,
    base_unit_snapshot VARCHAR(20) NOT NULL,
    batch_no VARCHAR(100) NOT NULL,
    full_cartons INT UNSIGNED NOT NULL DEFAULT 0,
    loose_units BIGINT UNSIGNED NOT NULL DEFAULT 0,
    calculated_total_units BIGINT UNSIGNED NULL,
    total_units BIGINT UNSIGNED NULL,
    total_units_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    remarks VARCHAR(1000) NULL,
    sort_order INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_item_movement_sort (movement_id, sort_order),
    KEY idx_item_product (product_id),
    KEY idx_item_batch (batch_no),
    KEY idx_item_product_name (product_name_snapshot),
    CONSTRAINT fk_item_movement FOREIGN KEY (movement_id) REFERENCES movement(id) ON DELETE CASCADE,
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE RESTRICT,
    CONSTRAINT chk_item_has_quantity CHECK (full_cartons > 0 OR loose_units > 0 OR total_units > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE movement_item_issue (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    movement_item_id BIGINT UNSIGNED NOT NULL,
    issue_type VARCHAR(40) NOT NULL,
    description VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_issue_type (movement_item_id, issue_type),
    KEY idx_issue_type (issue_type),
    CONSTRAINT fk_issue_item FOREIGN KEY (movement_item_id) REFERENCES movement_item(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE movement_photo (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    movement_id BIGINT UNSIGNED NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT UNSIGNED NOT NULL,
    width INT UNSIGNED NOT NULL,
    height INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_photo_movement (movement_id, created_at),
    CONSTRAINT fk_photo_movement FOREIGN KEY (movement_id) REFERENCES movement(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
