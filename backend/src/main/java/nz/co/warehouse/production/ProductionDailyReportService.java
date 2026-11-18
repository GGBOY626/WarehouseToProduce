package nz.co.warehouse.production;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import nz.co.warehouse.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

@Service @RequiredArgsConstructor
public class ProductionDailyReportService {
    private static final long MAX_SAFE_INTEGER=9_007_199_254_740_991L;
    private final ProductionTaskRepository tasks;
    private final ProductionDailyReportRepository reports;

    public record SaveRequest(@NotNull LocalDate reportDate,
                              @NotNull @Min(0) @Max(MAX_SAFE_INTEGER) Long groupACartons,
                              @NotNull @Min(0) @Max(MAX_SAFE_INTEGER) Long groupBCartons) {}
    public record Response(Long id,Long taskId,LocalDate reportDate,String productName,
                           long groupACartons,long groupBCartons,long totalCartons,Instant updatedAt) {}

    @Transactional(readOnly=true)
    public List<Response> list(long taskId){
        task(taskId);
        return reports.findAllByTaskIdOrderByReportDateDescIdDesc(taskId).stream().map(this::response).toList();
    }

    @Transactional
    public Response save(long taskId,SaveRequest request){
        var task=tasks.findByIdForUpdate(taskId).orElseThrow(()->BusinessException.notFound("PRODUCTION_TASK_NOT_FOUND","找不到该生产任务。"));
        if(task.getOutputProduct()==null)throw BusinessException.badRequest("TASK_OUTPUT_REQUIRED","请先在生产任务中关联成品，再登记生产日报。");
        var report=reports.findByTaskIdAndReportDate(taskId,request.reportDate()).orElseGet(()->{
            var created=new ProductionDailyReport();created.setTask(task);created.setReportDate(request.reportDate());return created;
        });
        report.setGroupACartons(request.groupACartons());report.setGroupBCartons(request.groupBCartons());
        return response(reports.saveAndFlush(report));
    }

    private ProductionTask task(long id){return tasks.findWithProductById(id).orElseThrow(()->BusinessException.notFound("PRODUCTION_TASK_NOT_FOUND","找不到该生产任务。"));}
    private Response response(ProductionDailyReport report){
        var task=report.getTask();
        return new Response(report.getId(),task.getId(),report.getReportDate(),task.getOutputProduct().getName(),
            report.getGroupACartons(),report.getGroupBCartons(),Math.addExact(report.getGroupACartons(),report.getGroupBCartons()),report.getUpdatedAt());
    }
}
