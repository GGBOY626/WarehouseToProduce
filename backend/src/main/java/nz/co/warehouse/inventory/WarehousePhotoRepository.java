package nz.co.warehouse.inventory;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface WarehousePhotoRepository extends JpaRepository<WarehousePhoto,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from WarehousePhoto p where p.id=:id")
    Optional<WarehousePhoto> lockById(long id);
}
