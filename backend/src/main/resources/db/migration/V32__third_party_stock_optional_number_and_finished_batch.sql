ALTER TABLE third_party_stock ALTER COLUMN warehouse_number DROP NOT NULL;
ALTER TABLE third_party_stock_item ADD COLUMN batch VARCHAR(100);
ALTER TABLE third_party_stock_item ADD COLUMN finished BOOLEAN;
