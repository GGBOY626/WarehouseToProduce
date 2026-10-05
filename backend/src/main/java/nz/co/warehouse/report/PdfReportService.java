package nz.co.warehouse.report;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.co.warehouse.common.BusinessException;
import nz.co.warehouse.movement.Movement;
import nz.co.warehouse.movement.MovementEnums;
import nz.co.warehouse.movement.MovementItem;
import nz.co.warehouse.movement.MovementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j @Service @RequiredArgsConstructor
public class PdfReportService {
    private static final String PDF_FONT = "/fonts/DroidSansFallbackFull.ttf";
    private static final DateTimeFormatter REPORT_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private final MovementRepository repository;
    @Value("${app.business-zone:Pacific/Auckland}") private String zone;

    @Transactional(readOnly = true)
    public byte[] generate(LocalDate from, LocalDate to, MovementEnums.Direction direction) {
        validateRange(from, to);
        ZoneId businessZone = ZoneId.of(zone);
        List<Movement> rows = repository.findReportRows(from.atStartOfDay(businessZone).toInstant(),
                to.plusDays(1).atStartOfDay(businessZone).toInstant(), MovementEnums.Status.ACTIVE);
        if (direction == MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION)
            rows = rows.stream().filter(row -> row.getDirection() == direction || row.isReturnMovement()).toList();
        else if (direction == MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE)
            rows = rows.stream().filter(row -> row.getDirection() == direction && !row.isReturnMovement()).toList();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(this::openFont, "WarehouseCN");
            builder.withHtmlContent(html(rows, from, to, direction, businessZone), null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (Exception exception) {
            log.error("PDF 生成失败 {} - {}", from, to, exception);
            throw new BusinessException("PDF_GENERATION_FAILED", "PDF 生成失败，请稍后重试或联系管理员。", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public byte[] generate(LocalDate from, LocalDate to) { return generate(from, to, null); }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw BusinessException.badRequest("INVALID_DATE_RANGE", "结束日期不能早于开始日期。");
        if (from.plusYears(1).isBefore(to)) throw BusinessException.badRequest("DATE_RANGE_TOO_LARGE", "单次导出日期范围不能超过一年。");
    }

    private InputStream openFont() {
        InputStream font = PdfReportService.class.getResourceAsStream(PDF_FONT);
        if (font == null) throw new IllegalStateException("JAR 中缺少 PDF 中文字体: " + PDF_FONT);
        return font;
    }

    private String html(List<Movement> rows, LocalDate from, LocalDate to,
                        MovementEnums.Direction selectedDirection, ZoneId businessZone) {
        StringBuilder html = new StringBuilder("""
                <!DOCTYPE html><html><head><meta charset="UTF-8"/><style>
                @page{size:A4 landscape;margin:7mm 4mm 8mm;@bottom-center{content:"第 " counter(page) " / " counter(pages) " 页";font-family:WarehouseCN;font-size:7px;color:#9aa8ae}}
                *{box-sizing:border-box}body{font-family:WarehouseCN;color:#172126;font-size:9px;line-height:1.28}h1{font-size:19px;margin:0 0 2px}
                .range{color:#52626b;margin-bottom:4px}.formula{background:#eef5f8;border-left:3px solid #174a68;padding:3px 7px;margin:0 0 7px}
                .report-section{margin:0 0 9px}.section-title{font-size:14px;color:#174a68;border-bottom:2px solid #174a68;padding-bottom:3px;margin:0 0 4px}
                .totals{page-break-inside:avoid;margin-top:4px}.totals .section-title{font-size:15px;background:#f4f8fa;padding:4px 6px;border-bottom-width:2px}
                .section-summary{float:right;font-size:8px;font-weight:normal;color:#60727b;margin-top:2px}
                .return-label{display:inline-block;background:#fff0d6;color:#8a5714;border:1px solid #e3b56d;padding:1px 5px;margin-left:5px;font-size:9px}
                .arrow{display:inline-block;width:15px;height:7px;border-top:1.5px solid #172126;margin:0 5px;position:relative;top:3px}
                .arrow-tip{display:block;width:6px;height:6px;border-top:1.5px solid #172126;border-right:1.5px solid #172126;position:absolute;right:0;top:-4px;transform:rotate(45deg)}
                table{width:100%;border-collapse:collapse;table-layout:fixed}th,td{border:1px solid #bcc8cd;padding:4px;vertical-align:middle;word-wrap:break-word}
                th{background:#e9f2f6;text-align:center;font-weight:bold}.text{text-align:left}.center{text-align:center}.number{text-align:right}
                .date-group td{background:#dcecf3;color:#174a68;text-align:center;font-size:10px;font-weight:bold;padding:4px}
                .carton-spec{font-size:7.5px;color:#718188;white-space:nowrap}.empty{text-align:center;padding:12px;color:#66777f}
                .direction-block+.direction-block{page-break-before:always}
                </style></head><body>
                """);
        html.append("<h1>仓库 / 生产车间物料流转记录</h1><div class='range'>日期范围：")
                .append(REPORT_DATE.format(from)).append(" 至 ").append(REPORT_DATE.format(to)).append("　导出范围：")
                .append(selectedDirection == null ? "全部方向" : directionText(selectedDirection)).append("</div>")
                .append("<div class='formula'><strong>数量计算：</strong>总数量 = 完整箱 × 每箱数量 + 散装数量（例：3 × 10 + 2 = 32）。</div>");
        if (selectedDirection == null || selectedDirection == MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION)
            appendDirectionBlock(html, rows, MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION, businessZone);
        if ((selectedDirection == null || selectedDirection == MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION)
                && rows.stream().anyMatch(Movement::isReturnMovement))
            appendReturnBlock(html, rows, businessZone);
        if (selectedDirection == null || selectedDirection == MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE)
            appendDirectionBlock(html, rows, MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, businessZone);
        return html.append("</body></html>").toString();
    }

    private void appendDirectionBlock(StringBuilder html, List<Movement> allRows,
                                      MovementEnums.Direction direction, ZoneId businessZone) {
        List<Movement> rows = allRows.stream().filter(row -> row.getDirection() == direction && !row.isReturnMovement()).toList();
        String heading = directionMarkup(direction);
        html.append("<div class='direction-block'>");
        appendDetailTable(html, rows, heading, businessZone);
        appendTotalTable(html, rows, heading);
        html.append("</div>");
    }

    private void appendReturnBlock(StringBuilder html, List<Movement> allRows, ZoneId businessZone) {
        List<Movement> rows = allRows.stream().filter(Movement::isReturnMovement).toList();
        String heading = directionMarkup(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE) + " <span class='return-label'>退回</span>";
        html.append("<div class='direction-block'>");
        appendDetailTable(html, rows, heading, businessZone);
        appendTotalTable(html, rows, heading);
        html.append("</div>");
    }

    private void appendDetailTable(StringBuilder html, List<Movement> rows,
                                   String heading, ZoneId businessZone) {
        html.append("<section class='report-section'><h2 class='section-title'>").append(heading).append(" 明细表</h2>")
                .append("<table><thead><tr><th style='width:9%'>日期</th><th style='width:15%'>产品</th><th style='width:12%'>物料编码</th>")
                .append("<th style='width:11%'>物料批次</th><th style='width:11%'>完整箱</th><th style='width:8%'>散装数量</th>")
                .append("<th style='width:9%'>总数量</th><th style='width:10%'>产品备注</th><th style='width:7.5%'>出货人</th><th style='width:7.5%'>收货人</th></tr></thead><tbody>");
        int itemCount = 0;
        Map<LocalDate, Integer> dailyCounts = new LinkedHashMap<>();
        for (Movement movement : rows) {
            LocalDate date = movement.getMovementTime().atZone(businessZone).toLocalDate();
            dailyCounts.merge(date, movement.getItems().size(), Integer::sum);
        }
        LocalDate currentDate = null;
        for (Movement movement : rows) {
            LocalDate movementDate = movement.getMovementTime().atZone(businessZone).toLocalDate();
            if (!movementDate.equals(currentDate)) {
                currentDate = movementDate;
                html.append("<tr class='date-group'><td colspan='10'>").append(REPORT_DATE.format(movementDate))
                        .append("　当日 ").append(dailyCounts.get(movementDate)).append(" 条</td></tr>");
            }
            for (MovementItem item : movement.getItems()) {
            itemCount++;
            html.append("<tr><td class='center'>").append(REPORT_DATE.format(movementDate)).append("</td><td class='text'>")
                    .append(e(item.getProductNameSnapshot())).append("</td><td class='center'>").append(e(item.getBatchNo()))
                    .append("</td><td class='center'>").append(e(movement.getManufactureLot())).append("</td><td class='number'>").append(fullCartons(item))
                    .append("</td><td class='number'>").append(quantity(item, item.getLooseUnits())).append("</td><td class='number'>").append(totalQuantity(item))
                    .append("</td><td class='text'>").append(e(item.getRemarks())).append("</td><td class='center'>").append(e(movement.getSenderNameSnapshot()))
                    .append("</td><td class='center'>").append(e(movement.getReceiverNameSnapshot())).append("</td></tr>");
            }
        }
        if (itemCount == 0) html.append("<tr><td colspan='10' class='empty'>该方向暂无记录</td></tr>");
        html.append("</tbody></table></section>");
    }

    private void appendTotalTable(StringBuilder html, List<Movement> rows, String heading) {
        Map<TotalKey, TotalRow> totals = new LinkedHashMap<>();
        for (Movement movement : rows) for (MovementItem item : movement.getItems()) {
            TotalKey key = new TotalKey(item.getProductNameSnapshot(), item.getBatchNo(), movement.getManufactureLot(), item.getUnitsPerCartonSnapshot(), item.getBaseUnitSnapshot());
            totals.computeIfAbsent(key, ignored -> new TotalRow()).add(item);
        }
        int recordCount = rows.stream().mapToInt(movement -> movement.getItems().size()).sum();
        html.append("<section class='report-section totals'><h2 class='section-title'>").append(heading).append(" 总计表")
                .append("<span class='section-summary'>共 ").append(totals.size()).append(" 种物料 / ").append(recordCount).append(" 条记录</span></h2>")
                .append("<table><thead><tr><th>产品</th><th>物料编码</th><th>物料批次</th><th>每箱数量</th><th>完整箱合计</th><th>散装合计</th><th>总数量合计</th></tr></thead><tbody>");
        for (Map.Entry<TotalKey, TotalRow> entry : totals.entrySet()) {
            TotalKey key = entry.getKey(); TotalRow total = entry.getValue();
            html.append("<tr><td class='text'>").append(e(key.product())).append("</td><td class='center'>").append(e(key.materialCode())).append("</td><td class='center'>").append(e(key.batch()))
                    .append("</td><td class='number'>").append(key.unitsPerCarton() == null ? "-" : key.unitsPerCarton() + " " + e(key.unit()))
                    .append("</td><td class='number'>").append(total.cartons).append(" 箱</td><td class='number'>").append(total.loose).append(" ").append(e(key.unit()))
                    .append("</td><td class='number'>").append(total.unknown ? "含数量不确定项" : total.total + " " + e(key.unit())).append("</td></tr>");
        }
        if (totals.isEmpty()) html.append("<tr><td colspan='7' class='empty'>该方向暂无记录</td></tr>");
        html.append("</tbody></table></section>");
    }

    private String fullCartons(MovementItem item) {
        if (item.isQuantityUnknown()) return item.getFullCartons() + " 箱";
        String cartonSize = item.getUnitsPerCartonSnapshot() == null ? "规格未设置" : item.getUnitsPerCartonSnapshot() + " " + e(item.getBaseUnitSnapshot()) + "/箱";
        return item.getFullCartons() + " 箱<br/><span class='carton-spec'>" + cartonSize + "</span>";
    }
    private String quantity(MovementItem item, long value) { return item.isQuantityUnknown() ? "数量不确定" : value + " " + e(item.getBaseUnitSnapshot()); }
    private String totalQuantity(MovementItem item) {
        if (item.isQuantityUnknown() || item.getTotalUnits() == null) return "数量不确定";
        return item.getTotalUnits() + " " + e(item.getBaseUnitSnapshot()) + (item.isTotalUnitsOverridden() ? "<br/><span class='muted'>（人工调整）</span>" : "");
    }
    private String directionText(MovementEnums.Direction direction) { return direction == MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION ? "仓库到生产车间" : "生产车间到仓库"; }

    /* The bundled CJK font has no U+2192 glyph, so CSS draws the arrow instead of rendering '#'. */
    private String directionMarkup(MovementEnums.Direction direction) {
        String from = direction == MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION ? "仓库" : "生产车间";
        String to = direction == MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION ? "生产车间" : "仓库";
        return from + "<span class='arrow'><span class='arrow-tip'></span></span>" + to;
    }
    private String e(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    private record TotalKey(String product, String materialCode, String batch, Integer unitsPerCarton, String unit) {}
    private static final class TotalRow {
        private int cartons; private long loose; private long total; private boolean unknown;
        private void add(MovementItem item) {
            cartons += item.getFullCartons(); loose += item.getLooseUnits();
            if (item.isQuantityUnknown() || item.getTotalUnits() == null) unknown = true; else total += item.getTotalUnits();
        }
    }
}
