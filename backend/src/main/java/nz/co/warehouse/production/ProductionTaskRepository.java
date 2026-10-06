package nz.co.warehouse.production;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ProductionTaskRepository extends JpaRepository<ProductionTask,Long> {
    @EntityGraph(attributePaths={"product","targets","targets.product"})
    @Query("select t from ProductionTask t where (:status is null or t.status=:status) order by t.plannedDate desc,t.id desc")
    List<ProductionTask> search(@Param("status") ProductionTask.Status status);
    @EntityGraph(attributePaths={"product","targets","targets.product"}) Optional<ProductionTask> findWithProductById(Long id);
}
