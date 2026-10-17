CREATE TABLE warehouse_area (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kind VARCHAR(10) NOT NULL,
    name VARCHAR(60) NOT NULL UNIQUE,
    lanes INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_warehouse_area CHECK ((kind='RACK' AND lanes=3) OR (kind='AISLE' AND lanes IN (2,4)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO warehouse_area (kind,name,lanes,created_at,updated_at) VALUES
('RACK','A',3,NOW(6),NOW(6)),('RACK','B',3,NOW(6),NOW(6)),('RACK','C',3,NOW(6),NOW(6)),
('RACK','D',3,NOW(6),NOW(6)),('RACK','E',3,NOW(6),NOW(6)),('RACK','F',3,NOW(6),NOW(6)),
('RACK','G',3,NOW(6),NOW(6)),('RACK','H',3,NOW(6),NOW(6)),('RACK','I',3,NOW(6),NOW(6)),
('RACK','J',3,NOW(6),NOW(6)),('RACK','K',3,NOW(6),NOW(6)),('RACK','L',3,NOW(6),NOW(6)),
('RACK','M',3,NOW(6),NOW(6)),('AISLE','过道 1',2,NOW(6),NOW(6));

CREATE TABLE warehouse_pallet (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(60) NOT NULL UNIQUE,
    area_id BIGINT UNSIGNED NOT NULL,
    lane VARCHAR(10) NOT NULL,
    position_no INT NOT NULL,
    notes VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_pallet_area FOREIGN KEY(area_id) REFERENCES warehouse_area(id),
    CONSTRAINT uk_pallet_position UNIQUE(area_id,lane,position_no),
    CONSTRAINT chk_pallet_position CHECK(position_no BETWEEN 1 AND 9999),
    CONSTRAINT chk_pallet_lane CHECK(lane IN ('UPPER','MIDDLE','LOWER','LEFT_1','LEFT_2','RIGHT_1','RIGHT_2'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE warehouse_stock_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    pallet_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    material_code VARCHAR(100) NOT NULL,
    batch VARCHAR(100),
    unit VARCHAR(20),
    quantity BIGINT,
    cartons INT,
    CONSTRAINT fk_stock_pallet FOREIGN KEY(pallet_id) REFERENCES warehouse_pallet(id),
    CONSTRAINT fk_stock_product FOREIGN KEY(product_id) REFERENCES product(id),
    CONSTRAINT chk_stock_quantity CHECK(quantity IS NULL OR quantity>0),
    CONSTRAINT chk_stock_cartons CHECK(cartons IS NULL OR cartons>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- No foreign key to pallets: history remains available after removal.
CREATE TABLE warehouse_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    pallet_id BIGINT UNSIGNED,
    pallet_code VARCHAR(60),
    action VARCHAR(20) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    before_state TEXT,
    after_state TEXT,
    created_at DATETIME(6) NOT NULL,
    KEY idx_warehouse_event_pallet (pallet_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
