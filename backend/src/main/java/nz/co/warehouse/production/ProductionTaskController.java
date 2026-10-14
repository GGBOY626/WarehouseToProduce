package nz.co.warehouse.production;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/production-tasks") @RequiredArgsConstructor
public class ProductionTaskController {
    private final ProductionTaskService service;
    @PostMapping("/linkable") public java.util.List<ProductionTaskService.LinkOption> linkableBatches(@jakarta.validation.Valid @RequestBody ProductionTaskService.LinkRequest r){return service.linkableBatches(r);}
    @GetMapping public List<ProductionTaskService.Response> list(@RequestParam(required=false) ProductionTask.Status status){return service.list(status);}
    @GetMapping("/linkable") public List<ProductionTaskService.LinkOption> linkable(
            @RequestParam List<Long> productIds,@RequestParam nz.co.warehouse.movement.MovementEnums.Direction direction,
            @RequestParam(defaultValue="false") boolean returnMovement){return service.linkable(productIds,direction,returnMovement);}
    @GetMapping("/{id}") public ProductionTaskService.Response detail(@PathVariable long id){return service.detail(id);}
    @PostMapping public ProductionTaskService.Response create(@Valid @RequestBody ProductionTaskService.SaveRequest r){return service.create(r);}
    @PutMapping("/{id}") public ProductionTaskService.Response update(@PathVariable long id,@Valid @RequestBody ProductionTaskService.SaveRequest r){return service.update(id,r);}
    @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void delete(@PathVariable long id){service.delete(id);}
    @PatchMapping("/{id}/status") public ProductionTaskService.Response status(@PathVariable long id,@RequestParam ProductionTask.Status status){return service.status(id,status);}
}
