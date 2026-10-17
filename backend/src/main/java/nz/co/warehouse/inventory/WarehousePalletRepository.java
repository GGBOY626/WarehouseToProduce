package nz.co.warehouse.inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WarehousePalletRepository extends JpaRepository<WarehousePallet,Long> {
    List<WarehousePallet> findAllByOrderByUpdatedAtDescIdDesc();
    boolean existsByAreaId(Long areaId);
    boolean existsByAreaIdAndLaneIn(Long areaId,List<String> lanes);
    boolean existsByAreaIdAndLaneAndPositionAndIdNot(Long areaId,String lane,int position,Long id);
}
