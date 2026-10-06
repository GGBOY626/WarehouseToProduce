package nz.co.warehouse.production;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/production-tasks") @RequiredArgsConstructor
public class ProductionTaskController {
    private final ProductionTaskService service;
    @GetMapping public List<ProductionTaskService.Response> list(@RequestParam(required=false) ProductionTask.Status status){return service.list(status);}
    @GetMapping("/{id}") public ProductionTaskService.Response detail(@PathVariable long id){return service.detail(id);}
    @PostMapping public ProductionTaskService.Response create(@Valid @RequestBody ProductionTaskService.SaveRequest r){return service.create(r);}
    @PutMapping("/{id}") public ProductionTaskService.Response update(@PathVariable long id,@Valid @RequestBody ProductionTaskService.SaveRequest r){return service.update(id,r);}
    @PatchMapping("/{id}/status") public ProductionTaskService.Response status(@PathVariable long id,@RequestParam ProductionTask.Status status){return service.status(id,status);}
}
