# Flyway V20 recovery (MySQL 8)

V20 originally failed while dropping `warehouse_pallet.uk_pallet_position`. That unique index covers `(area_id, lane, position_no)` and is also the child-side index used by `fk_pallet_area`. The corrected migration keeps both the index and foreign key, and shifts rack positions in descending order.

## Before upgrading

Stop the backend and take the normal database backup. Do not apply the edited V20 to an environment where V20 has already succeeded: Flyway will reject its changed checksum. Investigate that environment separately.

Check the current version and any failed attempt:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
WHERE version IN ('19', '20')
ORDER BY installed_rank;

SHOW CREATE TABLE warehouse_pallet;
SHOW CREATE TABLE warehouse_stock_item;
SHOW CREATE TABLE movement;
SHOW CREATE TABLE warehouse_area;
```

Before retrying, check whether a rack slot already occupied at 13–16 would collide with a slot shifted from 09–12:

```sql
SELECT p.area_id, p.lane,
       IF(p.position_no BETWEEN 1 AND 12, p.position_no + 4, p.position_no) AS target_position,
       COUNT(*) AS pallet_count
FROM warehouse_pallet p
JOIN warehouse_area a ON a.id = p.area_id
WHERE a.kind = 'RACK'
GROUP BY p.area_id, p.lane, target_position
HAVING COUNT(*) > 1;
```

The result must be empty. If it is not, stop and reconcile physical rack assignments with warehouse staff. The migration retains the unique constraint and will reject a collision.

## Recover a failed V20 attempt

1. Confirm `flyway_schema_history` has **one failed V20 row** and no successful V20 row. Inspect the four tables above to determine which V20 changes already ran. In the observed failure at the index drop, rack names and aisle rows were renamed, the five new columns existed, and movement finished flags were populated; pallet positions had not yet moved.
2. Install the corrected V20 file. Its rack renames and column additions tolerate that observed partial state. If the database is in a different intermediate state, inspect it before proceeding; especially do not retry blindly if rack positions have already shifted.
3. Only after those checks, run Flyway `repair` against the **affected database** to remove the failed V20 history entry. Repair changes Flyway metadata; it does not undo any MySQL DDL or restore data.
4. Start the backend to run V20. Check for a successful V20 history row, retained `fk_pallet_area` and `uk_pallet_position`, rack positions 05–16, and unchanged business-row counts.

Do not reset the schema, clear business tables, disable foreign keys, or delete the migration history.
