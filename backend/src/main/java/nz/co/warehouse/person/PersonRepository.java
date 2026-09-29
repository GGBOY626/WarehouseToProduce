package nz.co.warehouse.person;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PersonRepository extends JpaRepository<Person, Long> {
    List<Person> findByActiveTrueAndNameContainingIgnoreCaseOrderByName(String q);
    List<Person> findByNameContainingIgnoreCaseOrderByActiveDescName(String q);
}
