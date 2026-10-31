package nz.co.warehouse.production;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/production-tasks/{taskId}/photos") @RequiredArgsConstructor
public class ProductionTaskPhotoController {
    private final ProductionTaskPhotoService service;
    @PostMapping public ProductionTaskService.PhotoResponse upload(@PathVariable long taskId,@RequestParam("file") MultipartFile file){return service.upload(taskId,file);}
    @GetMapping("/{photoId}/content") public ResponseEntity<Resource> content(@PathVariable long taskId,@PathVariable long photoId){return service.content(taskId,photoId);}
    @DeleteMapping("/{photoId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long taskId,@PathVariable long photoId){service.delete(taskId,photoId);}
}
