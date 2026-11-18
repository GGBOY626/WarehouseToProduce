package nz.co.warehouse.production;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductionDailyReportRepository extends JpaRepository<ProductionDailyReport,Long> {
    boolean existsByTaskId(long taskId);
    List<ProductionDailyReport> findAllByTaskIdOrderByReportDateDescIdDesc(long taskId);
    Optional<ProductionDailyReport> findByTaskIdAndReportDate(long taskId,LocalDate reportDate);
}
