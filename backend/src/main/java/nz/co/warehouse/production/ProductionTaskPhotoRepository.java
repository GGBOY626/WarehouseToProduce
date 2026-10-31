package nz.co.warehouse.production;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ProductionTaskPhotoRepository extends JpaRepository<ProductionTaskPhoto,Long> {
    long countByTaskId(long taskId);
    Optional<ProductionTaskPhoto> findByIdAndTaskId(long id,long taskId);
    List<ProductionTaskPhoto> findByTaskIdOrderByCreatedAtAsc(long taskId);
    @Query("select p from ProductionTaskPhoto p where p.task.id in :taskIds order by p.task.id, p.createdAt, p.id")
    List<ProductionTaskPhoto> findAllForTaskIds(@Param("taskIds") Collection<Long> taskIds);
}
