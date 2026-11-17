INSERT INTO warehouse_area (kind,name,lanes,created_at,updated_at)
SELECT 'RACK','RR',3,NOW(6),NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM warehouse_area WHERE name='RR');

INSERT INTO warehouse_area (kind,name,lanes,created_at,updated_at)
SELECT 'RACK','SS',3,NOW(6),NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM warehouse_area WHERE name='SS');
