package nz.co.warehouse.report;

import nz.co.warehouse.movement.MovementEnums;
import nz.co.warehouse.movement.MovementRepository;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfReportServiceTest {
    @Test
    void generatesPdfWithBundledChineseFont() throws Exception {
        MovementRepository repository=mock(MovementRepository.class);
        when(repository.findReportRows(any(),any(),any(MovementEnums.Status.class))).thenReturn(List.of());
        PdfReportService service=new PdfReportService(repository);
        ReflectionTestUtils.setField(service,"zone","Pacific/Auckland");
        byte[] pdf=service.generate(LocalDate.of(2026,9,1),LocalDate.of(2026,9,30));

        assertThat(pdf).hasSizeGreaterThan(1_000);
        assertThat(new String(pdf,0,4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try(PDDocument document=PDDocument.load(new ByteArrayInputStream(pdf))){
            String text=new PDFTextStripper().getText(document);
            assertThat(text).contains("仓库","2026-09-01","2026-09-30");
        }
    }
}
