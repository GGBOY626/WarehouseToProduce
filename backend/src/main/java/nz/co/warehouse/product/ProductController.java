package nz.co.warehouse.product;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/products") @RequiredArgsConstructor
public class ProductController {
    private final ProductService service;
    @GetMapping public List<ProductDtos.Response> search(@RequestParam(defaultValue="") String q, @RequestParam(defaultValue="false") boolean includeInactive) { return service.search(q, includeInactive); }
    @PostMapping public ProductDtos.Response create(@Valid @RequestBody ProductDtos.Request r) { return service.create(r); }
    @PutMapping("/{id}") public ProductDtos.Response update(@PathVariable long id, @Valid @RequestBody ProductDtos.Request r) { return service.update(id, r); }
    @PatchMapping("/{id}/status") public ProductDtos.Response status(@PathVariable long id, @RequestParam boolean active) { return service.setActive(id, active); }
    @DeleteMapping("/{id}") public void delete(@PathVariable long id) { service.delete(id); }
}
