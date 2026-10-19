from typing import Annotated, Literal
from pydantic import BaseModel, ConfigDict, Field

Text = Annotated[str, Field(max_length=200)]
Row = Literal['', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
              'AA', 'BB', 'CC', 'DD', 'EE', 'FF', 'GG', 'HH', 'II', 'JJ', 'KK', 'LL', 'MM']
Lane = Literal['', 'UPPER', 'MIDDLE', 'LOWER', 'LEFT_1', 'LEFT_2', 'RIGHT_1', 'RIGHT_2']


class StrictModel(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)


class Scope(StrictModel):
    query: Text | None = ''
    kind: Literal['', 'RACK', 'AISLE'] | None = ''
    row: Row | None = ''
    areaId: int | None = Field(default=None, gt=0)
    lane: Lane | None = ''
    unknownOnly: bool = False
    taskId: int | None = Field(default=None, gt=0)
    finished: bool | None = None
    hideFinished: bool = False


class ChatRequest(StrictModel):
    message: str = Field(min_length=1, max_length=2000)
    scope: Scope
    ticket: str = Field(pattern=r'^[0-9a-f-]{36}$')


class ToolFilters(StrictModel):
    query: Text = ''
    batch: str = Field(default='', max_length=100)
    row: Row = ''
    kind: Literal['', 'RACK', 'AISLE'] = ''
    lane: Lane = ''
    productId: int | None = Field(default=None, gt=0)
    unknownOnly: bool = False


class Amounts(BaseModel):
    quantities: dict[str, Annotated[str, Field(pattern=r'^\d+$')]]
    pending: int = Field(ge=0)
    unitUnknown: int = Field(ge=0)
    palletIds: list[int]


class Location(Amounts):
    location: str
    areaId: int
    lane: str
    position: int
    pallets: list[str]
    rawBatches: list[str]


class Batch(Amounts):
    label: str
    missing: bool
    locations: list[Location]


class Material(Amounts):
    name: str
    code: str
    productId: int | None
    batches: list[Batch]


class Inventory(BaseModel):
    materialCount: int = Field(ge=0)
    palletCount: int = Field(ge=0)
    pendingPalletCount: int = Field(ge=0)
    materials: list[Material]
    detailsOmitted: bool = False


class Target(BaseModel):
    productId: int
    productName: str
    materialCode: str | None = None
    baseUnit: str
    targetQuantity: int
    completedQuantity: int
    issuedQuantity: int
    returnedQuantity: int
    shortageQuantity: int
    unknownQuantityCount: int
    progressPercent: int
    estimatedUsedQuantity: float | None = None


class Output(BaseModel):
    productName: str
    baseUnit: str
    receivedQuantity: int
    unknownQuantityCount: int
    quantityBasis: Literal['BOX', 'UNIT'] | None = None
    netIssuedQuantity: int | None = None
    producedQuantity: float | None = None
    productionPercent: float | None = None
    lossPercent: float | None = None
    provisional: bool


class Movement(BaseModel):
    id: int
    recordNo: str
    direction: Literal['WAREHOUSE_TO_PRODUCTION', 'PRODUCTION_TO_WAREHOUSE']
    returnMovement: bool
    status: str
    unknownQuantityCount: int
    movementTime: str
    items: list[dict] | None = None


class Task(BaseModel):
    id: int
    name: str
    batchNo: str
    status: str
    progressPercent: int
    targets: list[Target]
    output: Output | None = None
    movements: list[Movement]


class ToolResult(BaseModel):
    source: str
    queriedAt: str
    scope: Scope
    inventory: Inventory | None = None
    task: Task | None = None


class Selection(StrictModel):
    evidence_ids: list[int] = Field(max_length=8)


class ChatResponse(BaseModel):
    answer: str
    results: list[ToolResult]
