package nz.co.warehouse.production;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import nz.co.warehouse.common.BusinessException;
import nz.co.warehouse.movement.*;
import nz.co.warehouse.product.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ProductionTaskService {
    private final ProductionTaskRepository tasks;
    private final MovementRepository movements;
    private final ProductService products;

    public record SaveRequest(@NotNull Long productId,@Positive long targetQuantity,@NotNull LocalDate plannedDate,
                              @NotBlank @Size(max=100) String batchNo,@Size(max=1000) String remarks) {}
    public record MovementResponse(Long id,String recordNo,MovementEnums.Direction direction,boolean returnMovement,
                                   Instant movementTime,int totalCartons,long totalQuantity,List<String> productNames) {}
    public record Response(Long id,Long productId,String productName,String baseUnit,long targetQuantity,LocalDate plannedDate,
                           String batchNo,ProductionTask.Status status,String remarks,long completedQuantity,int progressPercent,
                           int issueMovementCount,int returnMovementCount,List<MovementResponse> movements,Instant createdAt,Instant updatedAt) {}

    @Transactional(readOnly=true) public List<Response> list(ProductionTask.Status status){return tasks.search(status).stream().map(this::response).toList();}
    @Transactional(readOnly=true) public Response detail(long id){return response(get(id));}
    @Transactional public Response create(SaveRequest r){ProductionTask t=new ProductionTask();apply(t,r,false);return response(tasks.saveAndFlush(t));}
    @Transactional public Response update(long id,SaveRequest r){ProductionTask t=get(id);apply(t,r,true);return response(t);}
    @Transactional public Response status(long id,ProductionTask.Status status){ProductionTask t=get(id);t.setStatus(status);return response(t);}
    @Transactional(readOnly=true) public ProductionTask getLinkable(long id){ProductionTask t=get(id);if(t.getStatus()==ProductionTask.Status.COMPLETED||t.getStatus()==ProductionTask.Status.CANCELLED)throw BusinessException.badRequest("PRODUCTION_TASK_CLOSED","该生产任务已结束，不能继续关联流转记录。");return t;}

    private ProductionTask get(long id){return tasks.findWithProductById(id).orElseThrow(()->BusinessException.notFound("PRODUCTION_TASK_NOT_FOUND","找不到该生产任务。"));}
    private void apply(ProductionTask t,SaveRequest r,boolean editing){
        Product product=editing&&t.getProduct().getId().equals(r.productId())?products.getAny(r.productId()):products.getForMovement(r.productId(),ProductUsage.PRODUCTION_TO_WAREHOUSE,false);
        t.setProduct(product);t.setTargetQuantity(r.targetQuantity());t.setPlannedDate(r.plannedDate());t.setBatchNo(r.batchNo().trim());t.setRemarks(blank(r.remarks()));
    }
    private Response response(ProductionTask t){
        List<Movement> rows=movements.findByProductionTaskIdOrderByMovementTimeAsc(t.getId());
        long completed=rows.stream().filter(m->m.getStatus()==MovementEnums.Status.ACTIVE&&m.getDirection()==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&!m.isReturnMovement())
                .flatMap(m->m.getItems().stream()).filter(i->i.getProduct().getId().equals(t.getProduct().getId())).map(MovementItem::getTotalUnits).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        int progress=(int)Math.min(100,completed*100.0/t.getTargetQuantity());
        int issues=(int)rows.stream().filter(m->m.getItems().stream().anyMatch(i->!i.getIssues().isEmpty())).count();
        int returns=(int)rows.stream().filter(Movement::isReturnMovement).count();
        List<MovementResponse> linked=rows.stream().map(m->new MovementResponse(m.getId(),m.getRecordNo(),m.getDirection(),m.isReturnMovement(),m.getMovementTime(),m.getTotalCartons(),m.getItems().stream().map(MovementItem::getTotalUnits).filter(Objects::nonNull).mapToLong(Long::longValue).sum(),m.getItems().stream().map(MovementItem::getProductNameSnapshot).distinct().toList())).toList();
        return new Response(t.getId(),t.getProduct().getId(),t.getProduct().getName(),t.getProduct().getBaseUnit(),t.getTargetQuantity(),t.getPlannedDate(),t.getBatchNo(),t.getStatus(),t.getRemarks(),completed,progress,issues,returns,linked,t.getCreatedAt(),t.getUpdatedAt());
    }
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
}
