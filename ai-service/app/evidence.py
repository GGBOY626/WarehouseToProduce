from .schemas import ToolResult


def statements(results: list[ToolResult]) -> list[str]:
    """Only validated business values enter displayed prose; never render model-authored numbers."""
    lines: list[str] = []
    for result in results:
        stock = result.inventory
        if stock is not None:
            if stock.detailsOmitted:
                lines.append('当前范围明细过多，仅展示完整范围的总项数；请指定物料、批次或更小的货位范围后查看数量与位置明细。')
            lines.append(f'当前已录入托盘台账：查询范围内有 {stock.materialCount} 种物料、{stock.palletCount} 托盘，其中 {stock.pendingPalletCount} 托含待盘点数量。')
            lines.append('已确认数量按原单位分别展示；待盘点和单位未录入的数量不计入确认总量。未查询到记录不代表现场没有库存。')
        task = result.task
        if task is not None:
            lines.append('备料进度取各物料净发料与需求比例的平均值；净发料为发料减退料，仅有效记录计入。任务完成状态由员工确认。')
            if task.status not in ('COMPLETED', 'CANCELLED'):
                lines.append('当前任务仍在进行中，数量达到目标也不会自动标记完成；需核对发料、退料与回仓记录后人工确认。')
            if any(t.shortageQuantity > 0 for t in task.targets):
                lines.append('当前任务存在备料缺口。欠料仅表示任务净发料未达到需求，不表示全仓无库存。')
            if any(t.unknownQuantityCount for t in task.targets) or any(m.unknownQuantityCount for m in task.movements):
                lines.append('关联记录含未知数量，请通过原始记录入口核对；未知数量不是零。')
            if any(m.status != 'ACTIVE' for m in task.movements):
                lines.append('关联记录中有作废记录，已从任务数量统计中排除，保留入口供追溯。')
            out = task.output
            if out and out.quantityBasis:
                lines.append('生产完成度按回仓折算主料数量除以主料实际净发料计算，显示上限为 100%；退料不算成品回仓，历史数量不按新包装规格重算。')
                if out.productionPercent is None:
                    lines.append('生产完成度暂无法可靠计算，请核对主料净发料、未知数量及包装设置。')
                if out.provisional:
                    lines.append('损耗仍为暂估，差额可能是在制品或未登记回仓，不是最终实际损耗。')
                else:
                    lines.append('任务已标记完成，损耗仍需结合全部有效记录及现场情况核对。')
            elif out:
                lines.append('此任务缺少新版包装口径，旧版回仓计划进度不能解释为实际投入产出完成度或损耗率。')
    return list(dict.fromkeys(lines))
