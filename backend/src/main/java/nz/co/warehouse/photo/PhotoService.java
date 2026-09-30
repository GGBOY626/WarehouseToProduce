package nz.co.warehouse.photo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import nz.co.warehouse.common.BusinessException;
import nz.co.warehouse.movement.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;

@Slf4j @Service @RequiredArgsConstructor
public class PhotoService {
    private final MovementRepository movements;
    private final MovementPhotoRepository photos;
    @Value("${app.upload-dir:./data/uploads}") private String uploadDir;
    @Value("${app.max-photo-bytes:5242880}") private long maxBytes;

    public MovementDtos.PhotoResponse upload(long movementId, MultipartFile file) {
        Movement movement=movements.findById(movementId).orElseThrow(()->BusinessException.notFound("MOVEMENT_NOT_FOUND","找不到该流转记录。"));
        if(movement.getStatus()==MovementEnums.Status.VOID)throw BusinessException.badRequest("MOVEMENT_VOID","已作废记录不能补传照片。");
        if(photos.countByMovementId(movementId)>=10)throw BusinessException.badRequest("PHOTO_LIMIT","每条记录最多上传 10 张照片。");
        if(file.isEmpty()||file.getSize()>12L*1024*1024)throw BusinessException.badRequest("PHOTO_TOO_LARGE","原始图片不能为空且不能超过 12MB。");
        Path temp=null,target=null;
        try {
            temp=Files.createTempFile("warehouse-upload-",".tmp");file.transferTo(temp);
            BufferedImage source=ImageIO.read(temp.toFile());
            if(source==null)throw BusinessException.badRequest("PHOTO_FORMAT_UNSUPPORTED","当前图片格式暂不支持，请使用相机拍照或选择 JPEG/PNG 图片。");
            LocalDate date=LocalDate.now(ZoneId.of("Pacific/Auckland"));
            Path relative=Path.of("movements",String.valueOf(date.getYear()),String.format("%02d",date.getMonthValue()),String.valueOf(movementId),UUID.randomUUID()+".jpg");
            target=Path.of(uploadDir).toAbsolutePath().normalize().resolve(relative).normalize();
            if(!target.startsWith(Path.of(uploadDir).toAbsolutePath().normalize()))throw new SecurityException("非法图片路径");
            Files.createDirectories(target.getParent());
            String originalName=file.getOriginalFilename();
            boolean browserOptimized=originalName!=null&&originalName.endsWith(".optimized.jpg")
                    &&MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(file.getContentType())
                    &&source.getWidth()<=1600&&source.getHeight()<=1600&&file.getSize()<=900_000;
            BufferedImage output;
            if(browserOptimized){
                Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
                temp=null;
                output=source;
            }else{
                double quality=.88;
                do {
                    Thumbnails.of(temp.toFile()).useExifOrientation(true).size(2000,2000).keepAspectRatio(true).outputFormat("jpg").outputQuality(quality).toFile(target.toFile());
                    quality-=.08;
                } while(Files.size(target)>maxBytes&&quality>=.60);
                output=ImageIO.read(target.toFile());
            }
            if(Files.size(target)>maxBytes){Files.deleteIfExists(target);throw BusinessException.badRequest("PHOTO_TOO_LARGE","图片压缩后仍超过 5MB，请重新拍摄或选择较小图片。");}
            return saveMetadata(movement,file.getOriginalFilename(),relative,target,output);
        } catch(BusinessException ex){throw ex;} catch(Exception ex){if(target!=null)try{Files.deleteIfExists(target);}catch(IOException ignored){}log.error("图片上传失败 movementId={}",movementId,ex);throw new BusinessException("PHOTO_UPLOAD_FAILED","图片处理失败，请重新选择后再试。",HttpStatus.INTERNAL_SERVER_ERROR);} finally {if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
    }

    @Transactional
    protected MovementDtos.PhotoResponse saveMetadata(Movement movement,String originalName,Path relative,Path target,BufferedImage image)throws IOException{
        MovementPhoto p=new MovementPhoto();p.setMovement(movement);p.setFilePath(relative.toString().replace('\\','/'));p.setOriginalName(originalName==null?"照片.jpg":originalName);p.setMimeType(MediaType.IMAGE_JPEG_VALUE);p.setFileSize(Files.size(target));p.setWidth(image.getWidth());p.setHeight(image.getHeight());p=photos.save(p);
        return new MovementDtos.PhotoResponse(p.getId(),"/api/movements/"+movement.getId()+"/photos/"+p.getId()+"/content",p.getOriginalName(),p.getMimeType(),p.getFileSize(),p.getWidth(),p.getHeight(),p.getCreatedAt());
    }

    @Transactional(readOnly=true)
    public ResponseEntity<Resource> content(long movementId,long photoId){
        MovementPhoto p=photos.findByIdAndMovementId(photoId,movementId).orElseThrow(()->BusinessException.notFound("PHOTO_NOT_FOUND","找不到该照片。"));Path path=resolve(p.getFilePath());if(!Files.isRegularFile(path))throw BusinessException.notFound("PHOTO_FILE_MISSING","照片文件不存在，请联系管理员检查备份。");return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.getMimeType())).cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).body(new FileSystemResource(path));
    }

    @Transactional
    public void delete(long movementId,long photoId){
        Movement m=movements.findById(movementId).orElseThrow(()->BusinessException.notFound("MOVEMENT_NOT_FOUND","找不到该流转记录。"));if(m.getStatus()==MovementEnums.Status.VOID)throw BusinessException.badRequest("MOVEMENT_VOID","已作废记录不能删除照片。");MovementPhoto p=photos.findByIdAndMovementId(photoId,movementId).orElseThrow(()->BusinessException.notFound("PHOTO_NOT_FOUND","找不到该照片。"));photos.delete(p);try{Files.deleteIfExists(resolve(p.getFilePath()));}catch(IOException ex){log.error("照片文件删除失败: {}",p.getFilePath(),ex);}
    }
    private Path resolve(String relative){Path root=Path.of(uploadDir).toAbsolutePath().normalize();Path path=root.resolve(relative).normalize();if(!path.startsWith(root))throw new SecurityException("非法图片路径");return path;}
}
