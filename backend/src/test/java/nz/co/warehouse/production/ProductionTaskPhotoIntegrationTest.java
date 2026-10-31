package nz.co.warehouse.production;

import nz.co.warehouse.auth.AuthController;
import nz.co.warehouse.product.ProductDtos;
import nz.co.warehouse.product.ProductService;
import nz.co.warehouse.product.ProductUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:production-task-photos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class ProductionTaskPhotoIntegrationTest {
    private static final Path storage = Path.of(System.getProperty("java.io.tmpdir"), "warehouse-task-photos-" + UUID.randomUUID());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { registry.add("app.upload-dir", storage::toString); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ProductionTaskService tasks;
    @Autowired ProductService products;
    private MockHttpSession session;
    private long materialId;

    @BeforeEach
    void setup() {
        session = new MockHttpSession();
        session.setAttribute(AuthController.AUTH_SESSION_KEY, true);
        String suffix = UUID.randomUUID().toString();
        materialId = products.create(new ProductDtos.Request("Photo material " + suffix, suffix, null,
                ProductUsage.WAREHOUSE_TO_PRODUCTION, 10, "个")).id();
    }

    @Test
    void taskPhotosAppearInListAndDetailAndAreReadableAndDeleted() throws Exception {
        ProductionTaskService.Response task = createTask();
        String uploadBody = mvc.perform(multipart(photoUrl(task.id())).file(image()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originalName").value("sample.png"))
                .andReturn().getResponse().getContentAsString();
        long photoId = mapper.readTree(uploadBody).get("id").asLong();

        String photoUrl = photoUrl(task.id(), photoId);
        mvc.perform(get("/api/production-tasks/" + task.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].url").value(photoUrl));
        String listBody = mvc.perform(get("/api/production-tasks")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<ProductionTaskService.Response> listed = mapper.readValue(listBody, new TypeReference<>() {});
        assertThat(listed).filteredOn(item -> item.id().equals(task.id())).singleElement()
                .satisfies(item -> assertThat(item.photos()).hasSize(1));
        mvc.perform(get(photoUrl)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));

        Path storedFile;
        try (var files = Files.walk(storage)) { storedFile = files.filter(Files::isRegularFile).findFirst().orElseThrow(); }
        mvc.perform(delete("/api/production-tasks/" + task.id() + "/photos/" + photoId).session(session))
                .andExpect(status().isNoContent());
        assertThat(Files.exists(storedFile)).isFalse();
        assertThat(tasks.detail(task.id()).photos()).isEmpty();
    }

    @Test
    void enforcesTenPhotoLimitAndRemovesFilesWhenTaskIsDeleted() throws Exception {
        ProductionTaskService.Response task = createTask();
        for (int i = 0; i < 10; i++) {
            mvc.perform(multipart(photoUrl(task.id())).file(image()).session(session)).andExpect(status().isOk());
        }
        mvc.perform(multipart(photoUrl(task.id())).file(image()).session(session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHOTO_LIMIT"));
        assertThat(tasks.list(null).getFirst().photos()).hasSize(10);

        tasks.delete(task.id());
        try (var files = Files.walk(storage)) {
            assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    private ProductionTaskService.Response createTask() {
        return tasks.create(new ProductionTaskService.SaveRequest(materialId, 1000L, LocalDate.now(), "PHOTO-LOT", null));
    }

    private String photoUrl(long taskId) { return "/api/production-tasks/" + taskId + "/photos"; }
    private String photoUrl(long taskId, long photoId) { return photoUrl(taskId) + "/" + photoId + "/content"; }

    private MockMultipartFile image() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 30, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "sample.png", "image/png", output.toByteArray());
    }
}
