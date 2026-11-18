ALTER TABLE third_party_stock MODIFY warehouse_number VARCHAR(100) NULL;
ALTER TABLE third_party_stock_item ADD COLUMN batch VARCHAR(100);
ALTER TABLE third_party_stock_item ADD COLUMN finished BOOLEAN;
