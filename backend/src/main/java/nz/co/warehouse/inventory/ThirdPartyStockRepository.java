package nz.co.warehouse.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ThirdPartyStockRepository extends JpaRepository<ThirdPartyStock,Long> {
    List<ThirdPartyStock> findAllByOrderByUpdatedAtDescIdDesc();
    boolean existsByWarehouseId(long warehouseId);
    boolean existsByWarehouseIdAndWarehouseNumberIgnoreCaseAndIdNot(long warehouseId,String warehouseNumber,long id);
}
