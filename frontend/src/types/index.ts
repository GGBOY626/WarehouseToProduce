export type Direction = "WAREHOUSE_TO_PRODUCTION" | "PRODUCTION_TO_WAREHOUSE";
export type ProductUsage = Direction;
export type MovementStatus = "ACTIVE" | "VOID";
export type IssueType =
  | "MISSING_LABEL"
  | "WRONG_LABEL"
  | "DAMAGED_CARTON"
  | "QUANTITY_MISMATCH"
  | "PACKAGING_ISSUE"
  | "OTHER";
export interface Product {
  id: number;
  name: string;
  materialCode: string;
  materialBatch?: string;
  defaultUnitsPerCarton?: number;
  baseUnit?: string;
  quantityUnknown: boolean;
  movementDirection?: ProductUsage;
  active: boolean;
  duplicateName?: boolean;
}
export interface Person {
  id: number;
  name: string;
  remarks?: string;
  active: boolean;
}
export interface IssueInput {
  type: IssueType;
  description?: string;
}
export interface ItemInput {
  productId?: number;
  product?: Product;
  batchNo: string;
  fullCartons: number;
  looseUnits: number;
  totalUnits?: number;
  quantityUnknown: boolean;
  remarks: string;
  issues: IssueInput[];
  manualTotal: boolean;
}
export interface MovementDraft {
  idempotencyKey: string;
  direction: Direction;
  movementTime: string;
  senderPersonId?: number;
  receiverPersonId?: number;
  manufactureLot: string;
  returnMovement: boolean;
  remarks: string;
  items: ItemInput[];
}
export interface Photo {
  id: number;
  url: string;
  originalName: string;
  mimeType: string;
  fileSize: number;
  width: number;
  height: number;
  createdAt: string;
}
export interface MovementItem {
  id: number;
  productId: number;
  productName: string;
  sku?: string;
  unitsPerCarton?: number;
  baseUnit: string;
  batchNo: string;
  fullCartons: number;
  looseUnits: number;
  calculatedTotalUnits?: number;
  totalUnits?: number;
  totalUnitsOverridden: boolean;
  quantityUnknown: boolean;
  remarks?: string;
  issues: { id: number; type: IssueType; description?: string }[];
}
export interface MovementDetail {
  id: number;
  recordNo: string;
  direction: Direction;
  movementTime: string;
  senderPersonId: number;
  senderName: string;
  receiverPersonId: number;
  receiverName: string;
  manufactureLot?: string;
  returnMovement: boolean;
  totalCartons: number;
  remarks?: string;
  status: MovementStatus;
  voidReason?: string;
  items: MovementItem[];
  photos: Photo[];
  hasIssues: boolean;
  missingPhoto: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface MovementSummary {
  id: number;
  recordNo: string;
  direction: Direction;
  movementTime: string;
  senderName: string;
  receiverName: string;
  returnMovement: boolean;
  totalCartons: number;
  totalQuantity: number;
  status: MovementStatus;
  productNames: string[];
  itemCount: number;
  photoCount: number;
  hasIssues: boolean;
  hasUnknownQuantity: boolean;
}
export interface MovementPage {
  content: MovementSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface TodayStats {
  totalCartons: number;
  totalQuantity: number;
  unknownItemCount: number;
  products: { productName: string; fullCartons: number; totalQuantity: number; unknownItemCount: number }[];
}
export interface QueryStats {
  directions: {
    direction: Direction;
    totalCartons: number;
    totalQuantity: number;
    unknownItemCount: number;
    products: { productName: string; fullCartons: number; totalQuantity: number; unknownItemCount: number }[];
  }[];
}
