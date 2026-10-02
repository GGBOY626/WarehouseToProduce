package nz.co.warehouse.movement;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequestMapping("/api/movements") @RequiredArgsConstructor
public class MovementController {
    private final MovementService service;
    @PostMapping public MovementDtos.DetailResponse create(@Valid @RequestBody MovementDtos.SaveRequest r){return service.create(r);}
    @PutMapping("/{id}") public MovementDtos.DetailResponse update(@PathVariable long id,@Valid @RequestBody MovementDtos.SaveRequest r){return service.update(id,r);}
    @GetMapping("/{id}") public MovementDtos.DetailResponse detail(@PathVariable long id){return service.detail(id);}
    @PostMapping("/{id}/void") public MovementDtos.DetailResponse voidMovement(@PathVariable long id,@Valid @RequestBody MovementDtos.VoidRequest r){return service.voidMovement(id,r.reason());}
    @DeleteMapping("/{id}") public void delete(@PathVariable long id){service.delete(id);}
    @GetMapping public MovementDtos.PageResponse search(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) MovementEnums.Direction direction,@RequestParam(required=false) MovementEnums.Status status,
            @RequestParam(required=false) Boolean missingPhoto,@RequestParam(required=false) Boolean hasIssue,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.search(from,to,direction,status,missingPhoto,hasIssue,q,page,size);}
}
