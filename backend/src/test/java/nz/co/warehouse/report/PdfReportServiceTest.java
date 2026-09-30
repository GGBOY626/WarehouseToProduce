package nz.co.warehouse.report;

import nz.co.warehouse.movement.MovementEnums;
import nz.co.warehouse.movement.MovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfReportServiceTest {
    @Test
    void generatesPdfWithConfiguredChineseFont() {
        MovementRepository repository=mock(MovementRepository.class);
        when(repository.findReportRows(any(),any(),any(MovementEnums.Status.class))).thenReturn(List.of());
        PdfReportService service=new PdfReportService(repository);
        ReflectionTestUtils.setField(service,"zone","Pacific/Auckland");
        String font=windowsTestFont();
        Assumptions.assumeTrue(Files.isReadable(Path.of(font)),"Chinese test font is not installed");
        ReflectionTestUtils.setField(service,"configuredFont",font);

        byte[] pdf=service.generate(LocalDate.of(2026,9,1),LocalDate.of(2026,9,30));

        assertThat(pdf).hasSizeGreaterThan(1_000);
        assertThat(new String(pdf,0,4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    private String windowsTestFont(){
        String windows=System.getenv("WINDIR");
        return windows==null?"/app/fonts/WenQuanYiZenHei.ttf":windows+"/Fonts/simhei.ttf";
    }
}
