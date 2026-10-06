package nz.co.warehouse.photo;

import lombok.RequiredArgsConstructor;
import nz.co.warehouse.common.BusinessException;
import nz.co.warehouse.movement.MovementEnums;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@RestController @RequestMapping("/api/record-originals") @RequiredArgsConstructor
public class RecordOriginalController {
    private final RecordOriginalRepository repository;
    @Value("${app.upload-dir:./data/uploads}") private String uploadDir;
    public record Response(Long id,LocalDate recordDate,MovementEnums.Direction direction,String originalName,String url) {}
    private Response response(RecordOriginal value) {
        return new Response(value.getId(),value.getRecordDate(),value.getDirection(),value.getOriginalName(),"/api/record-originals/"+value.getId()+"/content");
    }
    @GetMapping
    public List<Response> list(@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam MovementEnums.Direction direction) {
        if(from.isAfter(to)) throw BusinessException.badRequest("DATE_RANGE_INVALID","开始日期不能晚于结束日期。");
        return repository.findByRecordDateBetweenAndDirectionOrderByRecordDateDescIdDesc(from,to,direction).stream().map(this::response).toList();
    }
    @PostMapping
    public Response upload(@RequestParam LocalDate date,@RequestParam MovementEnums.Direction direction,@RequestParam MultipartFile file) throws IOException {
        if(file.isEmpty()||file.getSize()>12L*1024*1024) throw BusinessException.badRequest("PHOTO_TOO_LARGE","请选择不超过 12MB 的记录表照片。");
        Path directory=resolve("record-originals/"+date);
        Files.createDirectories(directory);
        Path temp=Files.createTempFile(directory,"upload-",".tmp");
        Path target=null;
        try {
            file.transferTo(temp);
            String format=readFormatAndValidate(temp);
            boolean png=format.equals("png");
            String relative="record-originals/"+date+"/"+UUID.randomUUID()+(png?".png":".jpg");
            target=resolve(relative);
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);
            RecordOriginal value=new RecordOriginal();
            value.setRecordDate(date);value.setDirection(direction);value.setFilePath(relative);
            String name=Optional.ofNullable(file.getOriginalFilename()).orElse("记录表照片");
            value.setOriginalName(name.substring(0,Math.min(name.length(),255)));
            value.setMimeType(png?MediaType.IMAGE_PNG_VALUE:MediaType.IMAGE_JPEG_VALUE);
            return response(repository.saveAndFlush(value));
        } catch(RuntimeException|IOException error) {
            if(target!=null) Files.deleteIfExists(target);
            throw error;
        } finally {
            Files.deleteIfExists(temp);
        }
    }
    private String readFormatAndValidate(Path path) throws IOException {
        try(ImageInputStream input=ImageIO.createImageInputStream(path.toFile())) {
            if(input==null) throw BusinessException.badRequest("PHOTO_FORMAT_UNSUPPORTED","暂不支持该照片格式，请使用 JPG 或 PNG。");
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw BusinessException.badRequest("PHOTO_FORMAT_UNSUPPORTED","暂不支持该照片格式，请使用 JPG 或 PNG。");
            ImageReader reader=readers.next();
            try {
                reader.setInput(input,true,true);
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("jpeg","jpg","png").contains(format)) throw BusinessException.badRequest("PHOTO_FORMAT_UNSUPPORTED","暂不支持该照片格式，请使用 JPG 或 PNG。");
                if((long)reader.getWidth(0)*reader.getHeight(0)>60_000_000L) throw BusinessException.badRequest("PHOTO_TOO_LARGE","图片尺寸过大，请选择较小的照片。");
                return format;
            } finally { reader.dispose(); }
        }
    }
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable long id) {
        RecordOriginal value=repository.findById(id).orElseThrow(()->BusinessException.notFound("PHOTO_NOT_FOUND","找不到该记录表照片。"));
        Path path=resolve(value.getFilePath());
        if(!Files.isRegularFile(path)) throw BusinessException.notFound("PHOTO_NOT_FOUND","记录表照片文件不存在。");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.getMimeType())).header("X-Content-Type-Options","nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).body(new FileSystemResource(path));
    }
    private Path resolve(String relative) {
        Path root=Path.of(uploadDir).toAbsolutePath().normalize();
        Path path=root.resolve(relative).normalize();
        if(!path.startsWith(root)) throw new SecurityException("非法图片路径");
        return path;
    }
}
