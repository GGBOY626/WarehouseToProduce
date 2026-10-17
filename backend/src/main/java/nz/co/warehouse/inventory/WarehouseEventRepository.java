package nz.co.warehouse.inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
public interface WarehouseEventRepository extends JpaRepository<WarehouseEvent,Long> {
    Page<WarehouseEvent> findByPalletId(Long palletId,Pageable pageable);
}
