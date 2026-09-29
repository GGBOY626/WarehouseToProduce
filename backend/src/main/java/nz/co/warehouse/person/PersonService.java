package nz.co.warehouse.person;

import lombok.RequiredArgsConstructor;
import nz.co.warehouse.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class PersonService {
    private final PersonRepository repository;
    @Transactional(readOnly=true)
    public List<PersonDtos.Response> search(String q, boolean includeInactive) {
        String term = q == null ? "" : q.trim();
        var list = includeInactive ? repository.findByNameContainingIgnoreCaseOrderByActiveDescName(term) : repository.findByActiveTrueAndNameContainingIgnoreCaseOrderByName(term);
        return list.stream().map(PersonDtos.Response::from).toList();
    }
    @Transactional public PersonDtos.Response create(PersonDtos.Request r) { Person p = new Person(); apply(p,r); return PersonDtos.Response.from(repository.save(p)); }
    @Transactional public PersonDtos.Response update(long id, PersonDtos.Request r) { Person p=get(id); apply(p,r); return PersonDtos.Response.from(p); }
    @Transactional public PersonDtos.Response setActive(long id, boolean active) { Person p=get(id); p.setActive(active); return PersonDtos.Response.from(p); }
    public Person getActive(long id) { Person p=get(id); if(!p.isActive()) throw new BusinessException("PERSON_DISABLED","该人员已停用，请重新选择。", HttpStatus.CONFLICT); return p; }
    public Person getAny(long id) { return get(id); }
    private Person get(long id) { return repository.findById(id).orElseThrow(() -> BusinessException.notFound("PERSON_NOT_FOUND","找不到该人员。")); }
    private void apply(Person p, PersonDtos.Request r) { p.setName(r.name().trim()); p.setRemarks(r.remarks()==null||r.remarks().isBlank()?null:r.remarks().trim()); }
}
