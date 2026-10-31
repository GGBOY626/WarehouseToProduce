package nz.co.warehouse.production;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import nz.co.warehouse.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionTaskPhotoService {
    private static final long MAX_ORIGINAL_BYTES = 12L * 1024 * 1024;
    private final ProductionTaskRepository tasks;
    private final ProductionTaskPhotoRepository photos;

    @Value("${app.upload-dir:./data/uploads}")
    private String uploadDir;

    @Value("${app.max-photo-bytes:5242880}")
    private long maxBytes;

    @Transactional
    public ProductionTaskService.PhotoResponse upload(long taskId, MultipartFile file) {
        ProductionTask task = tasks.findById(taskId).orElseThrow(() ->
                BusinessException.notFound("PRODUCTION_TASK_NOT_FOUND", "找不到该生产任务。"));
        if (photos.countByTaskId(taskId) >= 10) {
            throw BusinessException.badRequest("PHOTO_LIMIT", "每个生产任务最多上传 10 张照片。");
        }
        if (file.isEmpty() || file.getSize() > MAX_ORIGINAL_BYTES) {
            throw BusinessException.badRequest("PHOTO_TOO_LARGE", "原始照片不能为空且不能超过 12MB。");
        }

        Path temp = null;
        Path target = null;
        try {
            temp = Files.createTempFile("warehouse-task-upload-", ".tmp");
            file.transferTo(temp);

            LocalDate date = LocalDate.now(ZoneId.of("Pacific/Auckland"));
            Path relative = Path.of("production-tasks", String.valueOf(date.getYear()),
                    String.format("%02d", date.getMonthValue()), String.valueOf(taskId), UUID.randomUUID() + ".jpg");
            Path root = Path.of(uploadDir).toAbsolutePath().normalize();
            target = root.resolve(relative).normalize();
            if (!target.startsWith(root)) throw new SecurityException("非法照片路径");
            Files.createDirectories(target.getParent());

            String originalName = file.getOriginalFilename();
            int[] dimensions = null;
            boolean optimizedCandidate = originalName != null && originalName.endsWith(".optimized.jpg")
                    && MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(file.getContentType()) && file.getSize() <= 280_000;
            if (optimizedCandidate) dimensions = readDimensions(temp);
            boolean browserOptimized = dimensions != null && dimensions[0] <= 1280 && dimensions[1] <= 1280;
            if (browserOptimized) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                temp = null;
            } else {
                BufferedImage source = ImageIO.read(temp.toFile());
                if (source == null) {
                    throw BusinessException.badRequest("PHOTO_FORMAT_UNSUPPORTED", "当前图片格式暂不支持，请使用相机拍摄或选择 JPEG/PNG 图片。");
                }
                double quality = .88;
                do {
                    Thumbnails.of(temp.toFile()).useExifOrientation(true).size(2000, 2000)
                            .keepAspectRatio(true).outputFormat("jpg").outputQuality(quality).toFile(target.toFile());
                    quality -= .08;
                } while (Files.size(target) > maxBytes && quality >= .60);
                BufferedImage output = ImageIO.read(target.toFile());
                if (output == null) throw new IOException("Compressed image could not be read");
                dimensions = new int[]{output.getWidth(), output.getHeight()};
            }

            if (Files.size(target) > maxBytes) {
                throw BusinessException.badRequest("PHOTO_TOO_LARGE", "照片压缩后仍超过 5MB，请重新拍摄或选择较小的图片。");
            }
            ProductionTaskService.PhotoResponse response = saveMetadata(task, originalName, relative, target, dimensions[0], dimensions[1]);
            Path uploadedFile = target;
            afterRollback(() -> deleteFile(uploadedFile));
            return response;
        } catch (BusinessException ex) {
            deleteFile(target);
            throw ex;
        } catch (Exception ex) {
            deleteFile(target);
            log.error("生产任务照片上传失败 taskId={}", taskId, ex);
            throw new BusinessException("PHOTO_UPLOAD_FAILED", "照片处理失败，请重新选择后再试。", HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            deleteFile(temp);
        }
    }

    private ProductionTaskService.PhotoResponse saveMetadata(ProductionTask task, String originalName,
                                                               Path relative, Path target, int width, int height) throws IOException {
        ProductionTaskPhoto photo = new ProductionTaskPhoto();
        photo.setTask(task);
        photo.setFilePath(relative.toString().replace('\\', '/'));
        String name = originalName == null || originalName.isBlank() ? "照片.jpg" : originalName;
        photo.setOriginalName(name.length() <= 255 ? name : name.substring(0, 255));
        photo.setMimeType(MediaType.IMAGE_JPEG_VALUE);
        photo.setFileSize(Files.size(target));
        photo.setWidth(width);
        photo.setHeight(height);
        photo = photos.saveAndFlush(photo);
        return new ProductionTaskService.PhotoResponse(photo.getId(),
                "/api/production-tasks/" + task.getId() + "/photos/" + photo.getId() + "/content",
                photo.getOriginalName(), photo.getMimeType(), photo.getFileSize(), photo.getWidth(), photo.getHeight(), photo.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> content(long taskId, long photoId) {
        ProductionTaskPhoto photo = photos.findByIdAndTaskId(photoId, taskId).orElseThrow(() ->
                BusinessException.notFound("PHOTO_NOT_FOUND", "找不到该照片。"));
        Path path = resolve(photo.getFilePath());
        if (!Files.isRegularFile(path)) {
            throw BusinessException.notFound("PHOTO_FILE_MISSING", "照片文件不存在，请联系管理员检查备份。");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.getMimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(new FileSystemResource(path));
    }

    @Transactional
    public void delete(long taskId, long photoId) {
        ProductionTaskPhoto photo = photos.findByIdAndTaskId(photoId, taskId).orElseThrow(() ->
                BusinessException.notFound("PHOTO_NOT_FOUND", "找不到该照片。"));
        String filePath = photo.getFilePath();
        photos.delete(photo);
        afterCommit(() -> deleteFile(resolve(filePath)));
    }

    @Transactional
    public void cleanupForTaskDeletion(long taskId) {
        List<String> filePaths = photos.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(ProductionTaskPhoto::getFilePath).toList();
        afterCommit(() -> filePaths.forEach(path -> deleteFile(resolve(path))));
    }

    private int[] readDimensions(Path image) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(image.toFile())) {
            if (input == null) return null;
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    private Path resolve(String relative) {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw new SecurityException("非法照片路径");
        return path;
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private void afterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) action.run();
            }
        });
    }

    private void deleteFile(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.error("生产任务照片文件清理失败: {}", path, ex);
        }
    }
}
