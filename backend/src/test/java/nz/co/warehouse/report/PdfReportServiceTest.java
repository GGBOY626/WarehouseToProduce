package nz.co.warehouse.report;

import nz.co.warehouse.movement.Movement;
import nz.co.warehouse.movement.MovementEnums;
import nz.co.warehouse.movement.MovementItem;
import nz.co.warehouse.movement.MovementRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfReportServiceTest {
    @Test void exportsPerItemMaterialBatchesInsteadOfTheSharedLegacyHeader() throws Exception {
        Movement row=movement(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,"2026-09-01T01:00:00Z","外盒","BATCH-CODE",1,0,10);
        row.setManufactureLot("LEGACY-HEADER");
        row.getItems().getFirst().setMaterialBatch("BATCH-A001");
        Movement second=movement(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,"2026-09-01T01:00:00Z","外盒","BATCH-CODE",2,0,20);
        second.getItems().getFirst().setMaterialBatch("BATCH-A002");
        row.addItem(second.getItems().getFirst());
        MovementRepository repository=mock(MovementRepository.class);
        when(repository.findReportRows(any(),any(),any(MovementEnums.Status.class))).thenReturn(List.of(row));
        PdfReportService service=new PdfReportService(repository);
        ReflectionTestUtils.setField(service,"zone","Pacific/Auckland");
        String text=pdfText(service.generate(LocalDate.of(2026,9,1),LocalDate.of(2026,9,1)));
        assertThat(text).contains("BATCH-A001","BATCH-A002").doesNotContain("LEGACY-HEADER");
        assertThat(nz.co.warehouse.movement.MaterialQuantity.group(row.getItems())).hasSize(2);
    }

    @Test
    void generatesFourTablesWithBundledChineseFont() throws Exception {
        MovementRepository repository = mock(MovementRepository.class);
        when(repository.findReportRows(any(), any(), any(MovementEnums.Status.class))).thenReturn(List.of(
                movement(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION, "2026-09-01T01:00:00Z", "纸箱", "MAT-001", 3, 2, 32),
                movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, "2026-09-01T03:00:00Z", "托盘", "MAT-002", 2, 5, 25)));
        PdfReportService service = new PdfReportService(repository);
        ReflectionTestUtils.setField(service, "zone", "Pacific/Auckland");

        byte[] pdf = service.generate(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(pdf).hasSizeGreaterThan(1_000);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        String text = pdfText(pdf);
        assertThat(text).contains("仓库", "生产车间", "明细表", "总计表", "日期", "产品", "物料编码", "物料批次",
                "完整箱", "产品备注", "出货人", "收货人", "纸箱", "MAT-001", "LOT-001", "2026/09/01", "2026/09/30", "当日");
        assertThat(text).doesNotContain("#");
    }

    @Test
    void exportsOnlyTheSelectedDirection() throws Exception {
        MovementRepository repository = mock(MovementRepository.class);
        when(repository.findReportRows(any(), any(), any(MovementEnums.Status.class))).thenReturn(List.of(
                movement(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION, "2026-09-01T01:00:00Z", "纸箱", "MAT-001", 3, 2, 32),
                movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, "2026-09-01T03:00:00Z", "托盘", "MAT-002", 2, 5, 25)));
        PdfReportService service = new PdfReportService(repository);
        ReflectionTestUtils.setField(service, "zone", "Pacific/Auckland");

        String text = pdfText(service.generate(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
                MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION));

        assertThat(text).contains("纸箱", "MAT-001").doesNotContain("托盘", "MAT-002");
    }

    @Test
    void warehouseExportAlsoShowsReturnsButNormalInboundExportDoesNot() throws Exception {
        MovementRepository repository = mock(MovementRepository.class);
        Movement returned = movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, "2026-09-01T04:00:00Z", "退回纸箱", "RETURN-001", 1, 0, 10);
        returned.setReturnMovement(true);
        when(repository.findReportRows(any(), any(), any(MovementEnums.Status.class))).thenReturn(List.of(
                movement(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION, "2026-09-01T01:00:00Z", "纸箱", "MAT-001", 3, 2, 32),
                movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, "2026-09-01T03:00:00Z", "托盘", "MAT-002", 2, 5, 25),
                returned));
        PdfReportService service = new PdfReportService(repository);
        ReflectionTestUtils.setField(service, "zone", "Pacific/Auckland");

        String warehouseText = pdfText(service.generate(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
                MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION));
        String productionText = pdfText(service.generate(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
                MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE));

        assertThat(warehouseText).contains("MAT-001", "RETURN-001", "退回").doesNotContain("MAT-002");
        assertThat(productionText).contains("MAT-002").doesNotContain("RETURN-001", "MAT-001");
    }

    @Test
    void keepsRepresentativeEightRowReportOnOnePage() throws Exception {
        MovementRepository repository = mock(MovementRepository.class);
        List<Movement> rows = List.of(
                movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, "2026-09-30T01:00:00Z", "关节饮出库", "N60542LSN", 35, 0, 630),
                movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, "2026-09-30T02:00:00Z", "关节饮出库", "N60542LSN", 35, 0, 630),
                movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, "2026-09-30T03:00:00Z", "清肺饮出库", "N60605LSN", 2, 162, 562),
                movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, "2026-09-30T04:00:00Z", "WhatAPoo", "N60745PSN", 1, 0, 250),
                movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, "2026-10-01T01:00:00Z", "WhatAPoo1400", "3A10814.43H101", 0, 1050, 1050),
                movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, "2026-10-01T02:00:00Z", "关节饮内盒", "3B03606003H102", 0, 157, 157),
                movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, "2026-10-02T01:00:00Z", "关节饮出库", "N60542LSN", 35, 12, 642),
                movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE, "2026-10-02T02:00:00Z", "关节饮出库", "N60542LSN", 18, 11, 335));
        when(repository.findReportRows(any(), any(), any(MovementEnums.Status.class))).thenReturn(rows);
        PdfReportService service = new PdfReportService(repository);
        ReflectionTestUtils.setField(service, "zone", "Pacific/Auckland");

        byte[] pdf = service.generate(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 2),
                MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE);
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdf))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void showsKnownCartonsWhenUnitQuantityIsUnknown() throws Exception {
        MovementRepository repository = mock(MovementRepository.class);
        Movement row = movement(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION, "2026-09-01T01:00:00Z", "未知件数产品", "UNKNOWN-001", 6, 0, 0);
        row.getItems().getFirst().setQuantityUnknown(true);
        row.getItems().getFirst().setTotalUnits(null);
        when(repository.findReportRows(any(), any(), any(MovementEnums.Status.class))).thenReturn(List.of(row));
        PdfReportService service = new PdfReportService(repository);
        ReflectionTestUtils.setField(service, "zone", "Pacific/Auckland");

        String text = pdfText(service.generate(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION));
        assertThat(text).contains("UNKNOWN-001", "6 箱");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "totalQuantity", row.getItems().getFirst())).isEqualTo("数量不确定");
    }

    @Test void exportsMixedSpecificationsTailCartonsAndLooseWithCombinedMaterialTotals() throws Exception {
        Movement mixed=movement(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,"2026-09-01T01:00:00Z","关节饮外盒","PACK-A",3,0,3000);
        mixed.setManufactureLot("A001");mixed.getItems().getFirst().setUnitsPerCartonSnapshot(1000);
        Movement other=movement(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,"2026-09-01T02:00:00Z","关节饮外盒","PACK-A",5,0,9000);
        other.setManufactureLot("A001");other.getItems().getFirst().setUnitsPerCartonSnapshot(1800);
        Movement tail=movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,"2026-09-01T03:00:00Z","关节饮外盒","PACK-A",15,2,15005);
        tail.setReturnMovement(true);tail.setManufactureLot("A001");
        tail.getItems().getFirst().setUnitsPerCartonSnapshot(1000);
        tail.getItems().getFirst().setTailCartons(1);tail.getItems().getFirst().setTailUnitsPerCarton(3L);
        Movement legacy=movement(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,"2026-09-01T04:00:00Z","旧散装记录","LEGACY",15,3,15003);
        legacy.getItems().getFirst().setUnitsPerCartonSnapshot(1000);
        MovementRepository repository=mock(MovementRepository.class);
        when(repository.findReportRows(any(),any(),any(MovementEnums.Status.class))).thenReturn(List.of(mixed,other,tail,legacy));
        PdfReportService service=new PdfReportService(repository);
        ReflectionTestUtils.setField(service,"zone","Pacific/Auckland");
        byte[] pdf=service.generate(LocalDate.of(2026,9,1),LocalDate.of(2026,9,1));
        String text=pdfText(pdf);
        assertThat(text).contains("1000 个/箱", "1800 个/箱", "3 满箱", "5 满箱", "8 箱", "12000 个", "16 箱", "15005 个", "1 尾箱", "3 个/箱", "旧散装记录", "15003 个").doesNotContain("#");
        String oldPacking=ReflectionTestUtils.invokeMethod(service,"fullCartons",legacy.getItems().getFirst());
        assertThat(oldPacking).contains("15 箱").doesNotContain("尾箱","16 箱");
        if(Boolean.getBoolean("warehouse.pdf.preview")) {
            java.nio.file.Files.write(java.nio.file.Path.of("target/packaging-preview.pdf"),pdf);
            try(PDDocument document=PDDocument.load(pdf)) {
                var renderer=new org.apache.pdfbox.rendering.PDFRenderer(document);
                for(int page=0;page<document.getNumberOfPages();page++)
                    javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page,130),"png",new java.io.File("target/packaging-preview-"+page+".png"));
            }
        }
    }

    @Test void exportsSameMaterialAndBatchAsSeparateSubBatchTotals() throws Exception {
        var a=movement(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,"2026-09-01T01:00:00Z","关节饮外盒","SAME-CODE",3,0,3000);
        var b=movement(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,"2026-09-01T02:00:00Z","关节饮外盒","SAME-CODE",5,0,9000);
        a.getItems().getFirst().setSubBatch("SUB-A");a.getItems().getFirst().setUnitsPerCartonSnapshot(1000);
        b.getItems().getFirst().setSubBatch("SUB-B");b.getItems().getFirst().setUnitsPerCartonSnapshot(1800);
        MovementRepository repository=mock(MovementRepository.class);
        when(repository.findReportRows(any(),any(),any(MovementEnums.Status.class))).thenReturn(List.of(a,b));
        PdfReportService service=new PdfReportService(repository);
        ReflectionTestUtils.setField(service,"zone","Pacific/Auckland");
        byte[] pdf=service.generate(LocalDate.of(2026,9,1),LocalDate.of(2026,9,1),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION);
        assertThat(pdfText(pdf)).contains("区分批次", "SUB-A", "SUB-B", "3000 个", "9000 个").doesNotContain("12000 个");
        StringBuilder totals=new StringBuilder();
        ReflectionTestUtils.invokeMethod(service,"appendTotalTable",totals,List.of(a,b),"测试");
        assertThat(totals.toString()).contains("共 2 种物料", "SUB-A", "SUB-B").doesNotContain("12000");
        assertThat((String)ReflectionTestUtils.invokeMethod(service,"batchLabel","LOT","<A&B>"))
                .contains("&lt;A&amp;B&gt;").doesNotContain("<A&B>");
        if(Boolean.getBoolean("warehouse.pdf.preview")) {
            java.nio.file.Files.write(java.nio.file.Path.of("target/sub-batch-preview.pdf"),pdf);
            try(PDDocument document=PDDocument.load(pdf)){
                var renderer=new org.apache.pdfbox.rendering.PDFRenderer(document);
                javax.imageio.ImageIO.write(renderer.renderImageWithDPI(0,130),"png",new java.io.File("target/sub-batch-preview.png"));
            }
        }
    }

    private Movement movement(MovementEnums.Direction direction, String time, String product, String code,
                              int cartons, long loose, long total) {
        Movement movement = new Movement();
        movement.setDirection(direction);
        movement.setMovementTime(Instant.parse(time));
        movement.setManufactureLot(direction == MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION ? "LOT-001" : null);
        movement.setSenderNameSnapshot("张三");
        movement.setReceiverNameSnapshot("李四");
        MovementItem item = new MovementItem();
        item.setProductNameSnapshot(product);
        item.setSkuSnapshot(null);
        item.setBatchNo(code);
        item.setUnitsPerCartonSnapshot(10);
        item.setBaseUnitSnapshot("个");
        item.setFullCartons(cartons);
        item.setLooseUnits(loose);
        item.setTotalUnits(total);
        item.setRemarks("测试备注");
        movement.addItem(item);
        return movement;
    }

    private String pdfText(byte[] pdf) throws Exception {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdf))) {
            return new PDFTextStripper().getText(document);
        }
    }
}
