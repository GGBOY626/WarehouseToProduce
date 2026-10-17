package nz.co.warehouse.inventory;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface WarehouseAreaRepository extends JpaRepository<WarehouseArea,Long> {
    List<WarehouseArea> findAllByOrderByIdAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from WarehouseArea a where a.id=:id")
    Optional<WarehouseArea> lockById(long id);
}
