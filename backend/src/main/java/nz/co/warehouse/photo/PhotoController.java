package nz.co.warehouse.photo;

import lombok.RequiredArgsConstructor;
import nz.co.warehouse.movement.MovementDtos;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/movements/{movementId}/photos") @RequiredArgsConstructor
public class PhotoController {
    private final PhotoService service;
    @PostMapping public MovementDtos.PhotoResponse upload(@PathVariable long movementId,@RequestParam("file") MultipartFile file){return service.upload(movementId,file);}
    @GetMapping("/{photoId}/content") public ResponseEntity<Resource> content(@PathVariable long movementId,@PathVariable long photoId){return service.content(movementId,photoId);}
    @DeleteMapping("/{photoId}") public void delete(@PathVariable long movementId,@PathVariable long photoId){service.delete(movementId,photoId);}
}
