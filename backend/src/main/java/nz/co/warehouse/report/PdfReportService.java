package nz.co.warehouse.report;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.co.warehouse.common.BusinessException;
import nz.co.warehouse.movement.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j @Service @RequiredArgsConstructor
public class PdfReportService {
    private final MovementRepository repository;
    @Value("${app.business-zone:Pacific/Auckland}") private String zone;
    private static final String PDF_FONT="/fonts/DroidSansFallbackFull.ttf";

    @Transactional(readOnly=true)
    public byte[] generate(LocalDate from,LocalDate to){
        if(to.isBefore(from))throw BusinessException.badRequest("INVALID_DATE_RANGE","结束日期不能早于开始日期。");
        if(from.plusYears(1).isBefore(to))throw BusinessException.badRequest("DATE_RANGE_TOO_LARGE","单次导出日期范围不能超过一年。");
        ZoneId z=ZoneId.of(zone);List<Movement> rows=repository.findReportRows(from.atStartOfDay(z).toInstant(),to.plusDays(1).atStartOfDay(z).toInstant(),MovementEnums.Status.ACTIVE);
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            PdfRendererBuilder builder=new PdfRendererBuilder();builder.useFastMode();builder.useFont(this::openFont,"WarehouseCN");builder.withHtmlContent(html(rows,from,to,z),null);builder.toStream(out);builder.run();return out.toByteArray();
        }catch(Exception ex){log.error("PDF 生成失败 {} - {}",from,to,ex);throw new BusinessException("PDF_GENERATION_FAILED","PDF 生成失败，请稍后重试或联系管理员。",HttpStatus.INTERNAL_SERVER_ERROR);}
    }

    private InputStream openFont(){
        InputStream font=PdfReportService.class.getResourceAsStream(PDF_FONT);
        if(font==null)throw new IllegalStateException("JAR 中缺少 PDF 中文字体: "+PDF_FONT);
        return font;
    }

    private String html(List<Movement> rows,LocalDate from,LocalDate to,ZoneId z){
        StringBuilder b=new StringBuilder("""
        <!DOCTYPE html><html><head><meta charset="UTF-8"/><style>
        @page{size:A4 landscape;margin:12mm 10mm}*{box-sizing:border-box}body{font-family:WarehouseCN,sans-serif;color:#172126;font-size:9px}h1{font-size:18px;margin:0 0 4px}.range{color:#52626b;margin-bottom:12px}.movement{border:1px solid #9eacb3;margin:0 0 10px;page-break-inside:avoid}.head{background:#e9f2f6;padding:7px 8px}.head strong{font-size:11px}.meta{margin-top:3px;color:#42545d}table{width:100%;border-collapse:collapse;table-layout:fixed}th,td{border-top:1px solid #cbd4d8;border-right:1px solid #dce2e5;padding:5px;vertical-align:top;word-wrap:break-word}th{background:#f3f5f6;text-align:left}th:last-child,td:last-child{border-right:0}.notes{padding:6px 8px;border-top:1px solid #dce2e5}.summary{margin-top:14px;border-top:2px solid #174a68;padding-top:8px}.void{color:#b4232c}</style></head><body>
        """);b.append("<h1>仓库 / 生产车间物料流转记录</h1><div class='range'>日期范围：").append(from).append(" ～ ").append(to).append("</div>");
        for(Movement m:rows){b.append("<section class='movement'><div class='head'><strong>").append(e(m.getRecordNo())).append(" · ").append(direction(m)).append("</strong><div class='meta'>").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(m.getMovementTime().atZone(z))).append("　发送人：").append(e(m.getSenderNameSnapshot())).append("　接收人：").append(e(m.getReceiverNameSnapshot()));if(m.getManufactureLot()!=null)b.append("　物料批次：").append(e(m.getManufactureLot()));b.append("　照片：").append(m.getPhotos().isEmpty()?"无":"有（"+m.getPhotos().size()+" 张）").append("</div></div><table><thead><tr><th style='width:18%'>产品</th><th style='width:11%'>物料编码</th><th style='width:7%'>完整箱</th><th style='width:8%'>散装</th><th style='width:9%'>总数量</th><th style='width:18%'>异常</th><th>产品备注</th></tr></thead><tbody>");for(MovementItem i:m.getItems()){String issues=i.getIssues().stream().map(x->issue(x.getIssueType())+(x.getDescription()==null?"":"："+x.getDescription())).collect(Collectors.joining("；"));b.append("<tr><td>").append(e(i.getProductNameSnapshot())).append(i.getSkuSnapshot()==null?"":"<br/>SKU "+e(i.getSkuSnapshot())).append("</td><td>").append(e(i.getBatchNo())).append("</td><td>").append(i.getFullCartons()).append("</td><td>").append(i.getLooseUnits()).append(" ").append(e(i.getBaseUnitSnapshot())).append("</td><td>").append(i.getTotalUnits()==null?"—":i.getTotalUnits()+" "+e(i.getBaseUnitSnapshot())).append(i.isTotalUnitsOverridden()?"<br/>（人工调整）":"").append("</td><td>").append(issues.isBlank()?"无":e(issues)).append("</td><td>").append(e(i.getRemarks())).append("</td></tr>");}b.append("</tbody></table>");if(m.getRemarks()!=null)b.append("<div class='notes'>整单备注：").append(e(m.getRemarks())).append("</div>");b.append("</section>");}
        long outbound=rows.stream().filter(m->m.getDirection()==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION).count();int cartons=rows.stream().mapToInt(Movement::getTotalCartons).sum();long missing=rows.stream().filter(m->m.getPhotos().isEmpty()).count();long issues=rows.stream().filter(m->m.getItems().stream().anyMatch(i->!i.getIssues().isEmpty())).count();Map<String,Long> loose=new LinkedHashMap<>();rows.forEach(m->m.getItems().forEach(i->loose.merge(i.getBaseUnitSnapshot(),i.getLooseUnits(),Long::sum)));
        b.append("<div class='summary'><strong>统计</strong><br/>流转次数：").append(rows.size()).append("　仓库 → 生产车间：").append(outbound).append("　生产车间 → 仓库：").append(rows.size()-outbound).append("　完整箱总数：").append(cartons).append("　散装：").append(loose.entrySet().stream().map(x->x.getValue()+" "+e(x.getKey())).collect(Collectors.joining("、"))).append("　缺少照片：").append(missing).append("　存在异常：").append(issues).append("</div></body></html>");return b.toString();
    }
    private String direction(Movement m){return m.getDirection()==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION?"仓库 → 生产车间":"生产车间 → 仓库";}
    private String issue(MovementEnums.IssueType t){return switch(t){case MISSING_LABEL->"缺少标签";case WRONG_LABEL->"标签错误";case DAMAGED_CARTON->"外箱破损";case QUANTITY_MISMATCH->"数量不一致";case PACKAGING_ISSUE->"包装异常";case OTHER->"其他";};}
    private String e(String s){if(s==null)return"";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
