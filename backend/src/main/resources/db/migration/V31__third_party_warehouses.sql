CREATE TABLE third_party_warehouse (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(500) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE third_party_stock (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    warehouse_number VARCHAR(100) NOT NULL,
    notes VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_external_stock_warehouse FOREIGN KEY(warehouse_id) REFERENCES third_party_warehouse(id),
    CONSTRAINT uk_external_warehouse_number UNIQUE(warehouse_id,warehouse_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE third_party_stock_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stock_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED,
    product_name VARCHAR(200) NOT NULL,
    material_code VARCHAR(100),
    production_number VARCHAR(100),
    material_batch VARCHAR(100),
    unit VARCHAR(20),
    quantity BIGINT,
    cartons INT,
    CONSTRAINT fk_external_item_stock FOREIGN KEY(stock_id) REFERENCES third_party_stock(id),
    CONSTRAINT fk_external_item_product FOREIGN KEY(product_id) REFERENCES product(id),
    CONSTRAINT chk_external_quantity CHECK(quantity IS NULL OR quantity>0),
    CONSTRAINT chk_external_cartons CHECK(cartons IS NULL OR cartons>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
