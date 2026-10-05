package nz.co.warehouse.photo;

import nz.co.warehouse.movement.MovementEnums;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface RecordOriginalRepository extends JpaRepository<RecordOriginal,Long> {
    List<RecordOriginal> findByRecordDateBetweenAndDirectionOrderByRecordDateDescIdDesc(LocalDate from,LocalDate to,MovementEnums.Direction direction);
}
