# 多规格、尾箱与散装登记

## 数据口径

- 一条 `MovementItem` 表示一种完整箱规格，可同时记录一种尾箱实际装量及真正散装。同一产品可以有多条明细，不需要新建产品。
- 完整箱规格使用已有 `units_per_carton_snapshot`；不修改产品默认规格。完整箱数量继续使用 `full_cartons`。
- 尾箱显式记录 `tail_cartons` 和 `tail_units_per_carton`，不通过取整、余数或数量大小推断。
- `loose_units` 只表示未装箱数量。
- 总箱数 = 完整箱数 + 尾箱箱数。
- 计算总数量 = 完整箱每箱数量 × 完整箱数 + 尾箱每箱实际数量 × 尾箱数 + 真正散装数量。
- 原有人工调整总数量、数量不确定功能保留；人工调整值仍是统计和导出的总量，详情注明人工调整。缺少历史箱规的记录仍允许未知总量。
- 列表、详情物料汇总、统计和 PDF 按物料编码、批次、基础单位分组，合并数量，同时保留满箱/尾箱各规格箱数。不同单位不会混加。方向筛选和退回逻辑保持原有口径。
- 同一物料多个规格属于同一流转单时，统计来源只出现一次；多张单仍弹窗选择，单张单直接进入详情。

## 数据库迁移

`backend/src/main/resources/db/migration/V11__add_carton_specs_and_tail_cartons.sql`：

1. `movement_item.tail_cartons`：非空整数，默认 `0`。
2. `movement_item.tail_units_per_carton`：可空整数；有尾箱时必须大于零，没有尾箱时为空。
3. 更新明细数量校验约束，使仅有尾箱的记录合法。
4. 新建 `product_carton_spec(product_id, units_per_carton)` 保存产品的常用规格，复合主键避免重复；默认规格继续使用原字段，不需要数据回填。

迁移没有重算历史数量，也没有合并、停用或删除任何产品。旧散装仍是散装，旧记录尾箱数为零。后续可在编辑页面明确改为尾箱。新前端和后端需一起发布；后端启动时由现有 Flyway 自动执行 V11。没有更改流转单编号生成逻辑。

原字段名称存在历史含义：`MovementItem.batchNo` 存物料编码，`Movement.manufactureLot` 存物料批次。本次沿用，普通成品入库也可选填批次；出库和退回仍要求填写批次。一张单继续共用一个批次。

## 操作验收

### 同物料多规格

1. 在产品管理创建或编辑“关节饮外盒”，默认每箱 `1000`，其他常用规格填写 `1800、2000`。
2. 新增流转记录，选择该产品，批次 `A001`；填写每箱 `1000`、完整箱 `3`。
3. 点击“添加同物料规格”，选择常用 `1800`，完整箱填写 `5`。也可直接输入临时规格。
4. 保存后，列表、详情物料汇总、统计及 PDF 应显示 **8 箱、12000 个**，保留 `1000 个/箱 × 3 满箱` 和 `1800 个/箱 × 5 满箱`。

### 尾箱与散装

1. 新增入库/退回记录，完整箱每箱 `1000`、完整箱数 `15`。
2. 展开“尾箱 / 真正散装”，尾箱箱数 `1`，每个尾箱实际数量 `3`，真正散装 `2`。
3. 保存后应显示 **16 箱、15005 个**，箱数明细 `15 满箱 + 1 尾箱`，另有散装 `2 个`。
4. 将真正散装改为 `0`，结果应为 **16 箱、15003 个**。
5. 清空尾箱实际数量、尾箱箱数改为 `0`，真正散装改为 `3`，结果应为 **15 箱、15003 个**。
6. 尾箱每箱实际数量不同，可添加同物料规格，分别填各组尾箱，不必填完整箱数。

## 主要文件

- 数据模型与写入：`Product.java`、`ProductDtos.java`、`ProductService.java`，`MovementItem.java`、`MovementDtos.java`、`MovementService.java`、`QuantityRules.java`。
- 读取与汇总：`MovementRepository.java`、`MaterialQuantity.java`；统一保留包装明细，使用原搜索条件。
- PDF：`PdfReportService.java`；总箱数及规格明细统一采用上述口径。
- 页面：`ProductView.vue`、`MovementFormView.vue`、`MovementDetailView.vue`、`HomeView.vue`、`MaterialSummary.vue`、`PackagingBreakdown.vue`、`types/index.ts`、`utils/quantity.ts`。
- 输入校验：`application.yml` 禁止小数静默转整数，`GlobalExceptionHandler.java` 返回 400 输入错误。
- 回归：`MovementServiceIntegrationTest.java`、`PdfReportServiceTest.java`、`frontend/tests/quantity.test.mjs`。

## 验证命令

使用 Java 21 和 Node.js 22.18+：

```powershell
cd backend
mvn test
# 可选：同时生成 PDF 和逐页 PNG，位于 backend/target/packaging-preview*
mvn test -Dwarehouse.pdf.preview=true
cd ../frontend
npm test
npm run build
```

测试覆盖多规格、满箱/尾箱/散装混合、仅尾箱、仅散装、同批次来源去重、不同批次隔离、旧记录编辑、产品默认规格变化后保留历史快照、负数/小数/缺失尾箱数量/溢出，以及 PDF 内容。

本次还在独立 MySQL 8.0 实例执行 V1–V11，并在 V11 前插入旧记录和两个历史产品。迁移后确认旧记录仍为 `15 箱 + 散装 3 个 = 15003 个`、尾箱为零、产品数量不变；新字段及常用规格表写入成功，应用的 Hibernate schema validate 通过。隔离的本地页面联调完成新增多规格、编辑尾箱和散装、保存后详情跳转，并检查了桌面和手机排版。

最终验证：45 项后端测试、6 项前端测试、生产构建通过；独立 MySQL 上使用真实 API 新增和编辑两个示例，并核对统计，分别得到 `8 箱 / 12000 个` 和 `16 箱 / 15005 个`。
