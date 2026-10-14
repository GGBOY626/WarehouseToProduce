export type Direction = "WAREHOUSE_TO_PRODUCTION" | "PRODUCTION_TO_WAREHOUSE";
export type ProductUsage = Direction;
export type MovementStatus = "ACTIVE" | "VOID";
export type ProductionTaskStatus = "PREPARING" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
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
  materialBatches?: string[];
  defaultUnitsPerCarton?: number;
  cartonSpecs?: number[];
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
  id?: number;
  unitsPerCarton?: number | null;
  tailCartons: number;
  tailUnitsPerCarton?: number | null;
  productId?: number;
  product?: Product;
  batchNo: string;
  materialBatch?: string | null;
  subBatch?: string | null;
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
  productionTaskId?: number;
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
  tailCartons: number;
  tailUnitsPerCarton?: number | null;
  totalCartons: number;
  id: number;
  productId: number;
  productName: string;
  sku?: string;
  unitsPerCarton?: number;
  baseUnit: string;
  batchNo: string;
  materialBatch?: string | null;
  subBatch?: string | null;
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
  materials: (MaterialSummary & { id: number; unknownItemCount: number })[];
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
  productionTaskId?: number;
  productionTaskName?: string;
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
export interface TaskMovementMaterial {
  productId: number; productName: string; materialCode: string; materialBatch?: string; subBatch?: string;
  baseUnit: string; totalCartons: number; totalQuantity: number; unknownQuantityCount: number; looseUnits: number; packaging: Packaging[];
}
export interface ProductionTaskMovement {
  items: TaskMovementMaterial[];
  id: number; recordNo: string; direction: Direction; returnMovement: boolean; movementTime: string;
  totalCartons: number; totalQuantity: number; productNames: string[]; status: MovementStatus; unknownQuantityCount: number; quantityUnit?: string;
}
export interface TaskBatchQuantity {
  materialBatch?: string | null; subBatch?: string | null; issuedQuantity: number; returnedQuantity: number; netQuantity: number; unknownQuantityCount: number;
}
export interface ProductionTaskOutput {
  boxesPerCarton?: number | null; unitsPerBox?: number | null; quantityBasis?: 'BOX' | 'UNIT' | null;
  primaryMaterialProductId?: number | null; primaryMaterialName?: string; primaryMaterialUnit?: string; netIssuedQuantity?: number | null;
  producedQuantity?: number | null; receivedBoxes?: number | null; productionPercent?: number | null; lossPercent?: number | null; provisional: boolean;
  materialBatch?: string | null;
  productId: number; productName: string; materialCode: string; baseUnit: string;
  targetQuantity: number; receivedQuantity: number; progressPercent: number; unknownQuantityCount: number;
}
export interface ProductionTaskOption { name: string; id: number; productName: string; batchNo: string; status: ProductionTaskStatus }
export interface ProductionTask {
  name: string;
  id: number; productId: number; productName: string; baseUnit?: string; targetQuantity: number;
  plannedDate: string; batchNo: string; status: ProductionTaskStatus; remarks?: string;
  completedQuantity: number; progressPercent: number; issueMovementCount: number; returnMovementCount: number;
  movements: ProductionTaskMovement[]; createdAt: string; updatedAt: string;
  targets: ProductionTaskTarget[];
  output?: ProductionTaskOutput;
}
export interface ProductionTaskTarget {
  batches?: TaskBatchQuantity[];
  materialBatch?: string | null;
  productId: number; productName: string; materialCode: string; baseUnit?: string;
  targetQuantity: number; completedQuantity: number; progressPercent: number;
  issuedQuantity: number; returnedQuantity: number; shortageQuantity: number;
  unknownQuantityCount: number; materialTarget: boolean; estimatedUsedQuantity?: number | null;
}
export interface Packaging { type: 'FULL' | 'TAIL'; unitsPerCarton: number | null; cartons: number }
export interface MaterialSummary {
  tailCartons: number;
  totalCartons: number;
  packaging: Packaging[];
  productName: string;
  materialCode: string;
  materialBatch?: string | null;
  subBatch?: string | null;
  baseUnit: string;
  fullCartons: number;
  looseUnits: number;
  totalQuantity?: number | null;
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
  items: (MaterialSummary & { id: number; quantityUnknown: boolean })[];
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
    products: (MaterialSummary & { totalQuantity: number; unknownItemCount: number; movements: StatMovement[] })[];
  }[];
}
export interface StatMovement {
  tailCartons: number;
  totalCartons: number;
  packaging: Packaging[];
  looseUnits: number;
  returnMovement: boolean;
  photoCount: number;
  id: number;
  recordNo: string;
  movementTime: string;
  direction: Direction;
  senderName: string;
  receiverName: string;
  fullCartons: number;
  totalQuantity: number;
  unknownItemCount: number;
}
