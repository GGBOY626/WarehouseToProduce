package nz.co.warehouse.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("select p from Product p where (:includeInactive = true or p.active = true) and " +
           "(:q = '' or lower(p.name) like lower(concat('%', :q, '%')) or lower(coalesce(p.sku,'')) like lower(concat('%', :q, '%'))) order by p.active desc, p.name")
    List<Product> search(@Param("q") String q, @Param("includeInactive") boolean includeInactive);
}
