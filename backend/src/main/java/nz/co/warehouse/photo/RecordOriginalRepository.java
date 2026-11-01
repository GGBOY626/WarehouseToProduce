package nz.co.warehouse.photo;

import nz.co.warehouse.movement.MovementEnums;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecordOriginalRepository extends JpaRepository<RecordOriginal,Long> {
    @Query("select r from RecordOriginal r where r.direction=:direction and r.recordDate<=:to and r.recordEndDate>=:from order by r.recordEndDate desc, r.recordDate desc, r.id desc")
    List<RecordOriginal> findByRecordDateBetweenAndDirectionOrderByRecordDateDescIdDesc(@Param("from") LocalDate from,@Param("to") LocalDate to,@Param("direction") MovementEnums.Direction direction);
    Page<RecordOriginal> findByDirectionOrderByRecordEndDateDescRecordDateDescIdDesc(MovementEnums.Direction direction, Pageable pageable);
}
