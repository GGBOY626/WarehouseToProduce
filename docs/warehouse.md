# 仓库货位管理

入口：顶部导航 **仓库管理**，地址 `/warehouse`。查询和操作记录公开；录入、编辑、移位、移除托盘以及管理过道均要求管理员登录，后端统一校验会话。

## 现场录入

1. 初次部署通过 Flyway `V17__warehouse_inventory.sql` 建立 A–M 共 13 排货架，每排上、中、下三层。预设一个常规两排的“过道 1”，可重命名并新增其他过道。
2. 先在产品管理建档，再到仓库页选择库区、层位或排位，输入现场位置序号（1–9999）。系统建议该层/排第一个未录入的序号，但不把“未录入”认定为“现场空位”。
3. 每个货位只登记一托盘。同托盘可以有多种产品或多个批号，各行分别填写数量和可选箱数；数量留空表示待盘点。数量按产品基础单位记总数，箱数不自动折算。
4. 连续录入时勾选“保存后继续”，保留库区、层位并建议下一位置；物料重新填写，避免把上一托的批号、数量带入下一托。
5. 已有托盘点“编辑 / 移位”可修改物料和所在位置。“移除”释放货位，保留之前的物料快照和操作记录。

过道两排为左一、右一；四排增加左二、右二。现场应统一朝向来区分左右。左二或右二存在托盘时不能缩成两排；有托盘的过道不能删除。A–M 货架保持固定。

此阶段是现场库存台账，独立于现有生产任务和物料流转单。仓库操作不会自动生成流转记录，也不会自动扣减生产备料。之后如需联动，应明确出入库、退料、任务领料的对应关系后再实现。

## 数据与后续接入

数据分为 `warehouse_area`（库区）、`warehouse_pallet`（托盘位置）、`warehouse_stock_item`（托盘物料）和 `warehouse_event`（操作历史）。托盘编号和“库区＋层位/排位＋位置序号”均有唯一约束。

托盘和过道带 `version`。编辑、删除必须传入最近查询的版本；旧版本返回 409，调用方应重新读取并核对，不能直接覆盖。托盘移除不删除历史。已被在库物料引用的产品不能直接删除，可以停用；旧托盘仍可编辑。

后续 AI agent 可以复用以下 JSON 接口，写入仍须经过现有登录认证。当前未新增 agent 账号、外部 AI 服务或自动操作权限。

| 方法 | 地址 | 用途 |
| --- | --- | --- |
| GET | `/api/warehouse` | 库区及托盘物料完整列表 |
| GET | `/api/warehouse/history?page=0&palletId=123` | 操作历史，每页 30 条；省略 palletId 查询全仓 |
| POST | `/api/warehouse/pallets` | 录入托盘 |
| PUT | `/api/warehouse/pallets/{id}` | 更新托盘完整资料（包括全部物料行）或移位 |
| DELETE | `/api/warehouse/pallets/{id}?version=0` | 移除托盘 |
| POST | `/api/warehouse/aisles` | 新增过道 |
| PUT | `/api/warehouse/aisles/{id}` | 修改过道名称、两排/四排配置 |
| DELETE | `/api/warehouse/aisles/{id}?version=0` | 删除空过道 |

托盘写入示例（areaId、productId 使用实际查询到的 ID）：

```json
{
  "code": "TP-001",
  "areaId": 1,
  "lane": "LOWER",
  "position": 1,
  "notes": "靠门一侧",
  "items": [
    {"productId": 42, "batch": "22334", "quantity": 2000, "cartons": 2},
    {"productId": 42, "batch": "22335", "quantity": null, "cartons": 3}
  ],
  "version": 0
}
```

新建时可以省略 version。货架 lane 为 `UPPER / MIDDLE / LOWER`，过道为 `LEFT_1 / RIGHT_1` 或再加 `LEFT_2 / RIGHT_2`。过道请求为 `{"name":"A–B 之间","lanes":2,"version":0}`，lanes 仅允许 2 或 4。

## 验证

`WarehouseIntegrationTest` 覆盖公开查询、全部写入路径登录校验、混托、未知数量、同位置冲突、非法层位、过道缩排保护、移位及删除历史、版本冲突、产品删除保护与输入校验。前端 `warehouse.test.mjs` 覆盖层位/排位规则、连续录入位置建议和组合筛选。
