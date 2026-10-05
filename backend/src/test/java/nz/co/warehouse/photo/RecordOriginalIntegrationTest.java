package nz.co.warehouse.photo;

import nz.co.warehouse.auth.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:originals;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"})
@AutoConfigureMockMvc
class RecordOriginalIntegrationTest {
    static final Path storage;
    static { try { storage=Files.createTempDirectory("warehouse-originals-test-"); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("app.upload-dir",storage::toString); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test void anonymousReadsAuthenticatedUploadsAndDateDirectionFiltering() throws Exception {
        var output=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20,30,BufferedImage.TYPE_INT_RGB),"png",output);
        byte[] bytes=output.toByteArray();
        var file=new MockMultipartFile("file","手写原件.png","image/png",bytes);
        mvc.perform(multipart("/api/record-originals").file(file).param("date","2026-10-04").param("direction","WAREHOUSE_TO_PRODUCTION")).andExpect(status().isUnauthorized());
        var session=new MockHttpSession();session.setAttribute(AuthController.AUTH_SESSION_KEY,true);
        var result=mvc.perform(multipart("/api/record-originals").file(file).param("date","2026-10-04").param("direction","WAREHOUSE_TO_PRODUCTION").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordDate").value("2026-10-04")).andReturn();
        String url=mapper.readTree(result.getResponse().getContentAsString()).get("url").asText();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(bytes));
        mvc.perform(multipart("/api/record-originals").file(file).param("date","2026-10-04").param("direction","WAREHOUSE_TO_PRODUCTION").session(session)).andExpect(status().isOk());
        mvc.perform(multipart("/api/record-originals").file(file).param("date","2026-10-04").param("direction","PRODUCTION_TO_WAREHOUSE").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/record-originals").param("from","2026-10-04").param("to","2026-10-04").param("direction","WAREHOUSE_TO_PRODUCTION")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/record-originals").param("from","2026-10-04").param("to","2026-10-04").param("direction","PRODUCTION_TO_WAREHOUSE")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/record-originals").param("from","2026-10-05").param("to","2026-10-05").param("direction","WAREHOUSE_TO_PRODUCTION")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/record-originals").param("from","2026-10-06").param("to","2026-10-04").param("direction","WAREHOUSE_TO_PRODUCTION")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/record-originals").file(new MockMultipartFile("file","fake.png","image/png","not an image".getBytes())).param("date","2026-10-04").param("direction","WAREHOUSE_TO_PRODUCTION").session(session)).andExpect(status().isBadRequest());
    }
}
