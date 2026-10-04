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
