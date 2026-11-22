package nz.co.warehouse.production;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
public class ProductionRecipeController {
    private final ProductionRecipeService service;
    @GetMapping("/api/products/{outputProductId}/recipes")
    public List<ProductionRecipeService.Response> list(@PathVariable long outputProductId) { return service.list(outputProductId); }
    @PostMapping("/api/products/{outputProductId}/recipes")
    public ProductionRecipeService.Response create(@PathVariable long outputProductId, @Valid @RequestBody ProductionRecipeService.SaveRequest request) { return service.create(outputProductId, request); }
    @PutMapping("/api/production-recipes/{id}")
    public ProductionRecipeService.Response update(@PathVariable long id, @Valid @RequestBody ProductionRecipeService.SaveRequest request) { return service.update(id, request); }
    @DeleteMapping("/api/production-recipes/{id}")
    public void delete(@PathVariable long id) { service.delete(id); }
}
