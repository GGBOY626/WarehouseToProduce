package nz.co.warehouse.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ThirdPartyWarehouseRepository extends JpaRepository<ThirdPartyWarehouse,Long> {
    List<ThirdPartyWarehouse> findAllByOrderByIdAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from ThirdPartyWarehouse w where w.id=:id")
    Optional<ThirdPartyWarehouse> lockById(@Param("id") long id);
}
