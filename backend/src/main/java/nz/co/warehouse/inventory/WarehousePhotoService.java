package nz.co.warehouse.inventory;

import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import nz.co.warehouse.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;

@Service @RequiredArgsConstructor
public class WarehousePhotoService {
    private final WarehousePhotoRepository photos;
    public record Response(Long id,String url) {}
    public static String url(Long id){return id==null?null:"/api/warehouse/photos/"+id+"/content";}
    @Transactional
    public Response upload(MultipartFile file){
        if(file.isEmpty()||file.getSize()>12L*1024*1024)throw BusinessException.badRequest("PHOTO_SIZE_INVALID","照片不能为空，且不能超过 12MB。");
        try{
            byte[] bytes=file.getBytes();
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext())throw BusinessException.badRequest("PHOTO_FORMAT_INVALID","请选择 JPG 或 PNG 照片。");
                var reader=readers.next();
                try{reader.setInput(input);String format=reader.getFormatName();
                    if(!format.equalsIgnoreCase("JPEG")&&!format.equalsIgnoreCase("PNG"))throw BusinessException.badRequest("PHOTO_FORMAT_INVALID","请选择 JPG 或 PNG 照片。");
                    if((long)reader.getWidth(0)*reader.getHeight(0)>40_000_000L)throw BusinessException.badRequest("PHOTO_DIMENSIONS_INVALID","照片像素过大，请缩小后上传。");
                }finally{reader.dispose();}
            }
            var output=new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(bytes)).useExifOrientation(true).size(1280,1280).outputFormat("jpg").outputQuality(.75).toOutputStream(output);
            if(output.size()>2*1024*1024)throw BusinessException.badRequest("PHOTO_SIZE_INVALID","压缩后的照片仍然过大，请选择较小的图片。");
            var photo=new WarehousePhoto();photo.setContent(output.toByteArray());photos.saveAndFlush(photo);return new Response(photo.getId(),url(photo.getId()));
        }catch(IOException e){throw BusinessException.badRequest("PHOTO_FORMAT_INVALID","照片无法读取，请重新选择 JPG 或 PNG 图片。");}
    }
    @Transactional(readOnly=true)
    public byte[] content(long id){return photos.findById(id).orElseThrow(()->BusinessException.notFound("PHOTO_NOT_FOUND","照片不存在。")).getContent();}
    // Once referenced by a saved record, an immutable photo is retained for its audit history.
    @Transactional
    public void discard(long id){photos.lockById(id).filter(p->!p.isRetained()).ifPresent(photos::delete);}
    public WarehousePhoto retain(long id){var p=photos.lockById(id).orElseThrow(()->BusinessException.notFound("PHOTO_NOT_FOUND","照片不存在，请重新上传。"));p.setRetained(true);return p;}
}
