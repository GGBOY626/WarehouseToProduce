ALTER TABLE movement_item
    DROP CHECK chk_item_has_quantity,
    ADD COLUMN quantity_unknown BOOLEAN NOT NULL DEFAULT FALSE AFTER total_units_overridden,
    ADD CONSTRAINT chk_item_has_quantity CHECK (
        quantity_unknown = TRUE OR full_cartons > 0 OR loose_units > 0 OR total_units > 0
    );
