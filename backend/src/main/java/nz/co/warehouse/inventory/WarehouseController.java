package nz.co.warehouse.inventory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController @RequestMapping("/api/warehouse") @RequiredArgsConstructor
public class WarehouseController {
    private final WarehouseService service;
    @GetMapping public WarehouseService.Overview overview(){return service.overview();}
    @GetMapping("/history") public WarehouseService.History history(@RequestParam(required=false) Long palletId,@RequestParam(defaultValue="0") int page){return service.history(palletId,page);}
    @PostMapping("/aisles") public WarehouseService.AreaResponse createAisle(@Valid @RequestBody WarehouseService.AreaRequest r){return service.createAisle(r);}
    @PutMapping("/aisles/{id}") public WarehouseService.AreaResponse updateAisle(@PathVariable long id,@Valid @RequestBody WarehouseService.AreaRequest r){return service.updateAisle(id,r);}
    @DeleteMapping("/aisles/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteAisle(@PathVariable long id,@RequestParam long version){service.deleteAisle(id,version);}
    @PostMapping("/pallets") public WarehouseService.PalletResponse create(@Valid @RequestBody WarehouseService.SaveRequest r){return service.create(r);}
    @PutMapping("/pallets/{id}") public WarehouseService.PalletResponse update(@PathVariable long id,@Valid @RequestBody WarehouseService.SaveRequest r){return service.update(id,r);}
    @DeleteMapping("/pallets/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long id,@RequestParam long version){service.delete(id,version);}
}
