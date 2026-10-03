ALTER TABLE product
    ADD COLUMN quantity_unknown BOOLEAN NOT NULL DEFAULT FALSE AFTER default_units_per_carton,
    MODIFY COLUMN base_unit VARCHAR(20) NULL;
