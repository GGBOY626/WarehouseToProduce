package nz.co.warehouse.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("select p from Product p where p.mergedInto is null and (:includeInactive = true or p.active = true) and " +
           "(:direction is null or p.movementDirection = :direction) and " +
           "(:q = '' or lower(p.name) like lower(concat('%', :q, '%')) or lower(p.materialCode) like lower(concat('%', :q, '%')) or exists (select b from p.materialBatches b where lower(b) like lower(concat('%', :q, '%'))) or lower(coalesce(p.materialBatch,'')) like lower(concat('%', :q, '%'))) order by p.active desc, p.name, p.materialCode")
    List<Product> search(@Param("q") String q, @Param("includeInactive") boolean includeInactive,
                         @Param("direction") ProductUsage direction);

    boolean existsByNameIgnoreCaseAndMaterialCodeIgnoreCaseAndMovementDirectionAndActiveTrue(String name, String materialCode, ProductUsage direction);

    boolean existsByNameIgnoreCaseAndMaterialCodeIgnoreCaseAndMovementDirectionAndActiveTrueAndIdNot(String name, String materialCode, ProductUsage direction, Long id);

    long countByNameIgnoreCaseAndActiveTrue(String name);

    long countByMergedIntoId(long id);

    @Query("select count(i) from MovementItem i where i.product.id = :productId")
    long countMovementItems(@Param("productId") long productId);

    @Query("select count(t) from ProductionTask t where t.product.id = :productId or t.outputProduct.id = :productId or exists (select x.id from ProductionTaskTarget x where x.task = t and x.product.id = :productId)")
    long countProductionTasks(@Param("productId") long productId);
}
