package nz.co.warehouse.movement;

public final class MovementEnums {
    private MovementEnums() {}
    public enum Direction { WAREHOUSE_TO_PRODUCTION, PRODUCTION_TO_WAREHOUSE }
    public enum Status { ACTIVE, VOID }
    public enum IssueType { MISSING_LABEL, WRONG_LABEL, DAMAGED_CARTON, QUANTITY_MISMATCH, PACKAGING_ISSUE, OTHER }
}
