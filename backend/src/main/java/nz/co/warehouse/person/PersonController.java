package nz.co.warehouse.person;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/persons") @RequiredArgsConstructor
public class PersonController {
    private final PersonService service;
    @GetMapping public List<PersonDtos.Response> search(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="false") boolean includeInactive){return service.search(q,includeInactive);}
    @PostMapping public PersonDtos.Response create(@Valid @RequestBody PersonDtos.Request r){return service.create(r);}
    @PutMapping("/{id}") public PersonDtos.Response update(@PathVariable long id,@Valid @RequestBody PersonDtos.Request r){return service.update(id,r);}
    @PatchMapping("/{id}/status") public PersonDtos.Response status(@PathVariable long id,@RequestParam boolean active){return service.setActive(id,active);}
}
