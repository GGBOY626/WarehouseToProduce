package nz.co.warehouse.inventory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController @RequestMapping("/api/warehouse/third-party") @RequiredArgsConstructor
public class ThirdPartyWarehouseController {
    private final ThirdPartyWarehouseService service;
    @GetMapping public ThirdPartyWarehouseService.Overview overview(){return service.overview();}
    @PostMapping("/warehouses") public ThirdPartyWarehouseService.WarehouseResponse createWarehouse(@Valid @RequestBody ThirdPartyWarehouseService.WarehouseRequest r){return service.createWarehouse(r);}
    @PutMapping("/warehouses/{id}") public ThirdPartyWarehouseService.WarehouseResponse updateWarehouse(@PathVariable long id,@Valid @RequestBody ThirdPartyWarehouseService.WarehouseRequest r){return service.updateWarehouse(id,r);}
    @DeleteMapping("/warehouses/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteWarehouse(@PathVariable long id,@RequestParam long version){service.deleteWarehouse(id,version);}
    @PostMapping("/stocks") public ThirdPartyWarehouseService.StockResponse createStock(@Valid @RequestBody ThirdPartyWarehouseService.StockRequest r){return service.createStock(r);}
    @PutMapping("/stocks/{id}") public ThirdPartyWarehouseService.StockResponse updateStock(@PathVariable long id,@Valid @RequestBody ThirdPartyWarehouseService.StockRequest r){return service.updateStock(id,r);}
    @DeleteMapping("/stocks/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteStock(@PathVariable long id,@RequestParam long version){service.deleteStock(id,version);}
}
