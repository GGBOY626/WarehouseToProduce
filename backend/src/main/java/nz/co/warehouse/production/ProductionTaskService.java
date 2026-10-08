package nz.co.warehouse.production;

import jakarta.validation.Valid;
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

    public record TargetRequest(@NotNull Long productId,@Positive long targetQuantity) {}
    public record SaveRequest(Long productId,Long targetQuantity,@NotNull LocalDate plannedDate,
                              @NotBlank @Size(max=100) String batchNo,@Size(max=1000) String remarks,
                              List<@NotNull @Valid TargetRequest> targets) {
        public SaveRequest(Long productId,long targetQuantity,LocalDate plannedDate,String batchNo,String remarks){
            this(productId,targetQuantity,plannedDate,batchNo,remarks,null);
        }
    }
    public record TargetResponse(Long productId,String productName,String materialCode,String baseUnit,long targetQuantity,
                                 long completedQuantity,int progressPercent,long issuedQuantity,long returnedQuantity,
                                 long shortageQuantity,int unknownQuantityCount,boolean materialTarget) {}
    public record MovementResponse(Long id,String recordNo,MovementEnums.Direction direction,boolean returnMovement,
                                   Instant movementTime,int totalCartons,long totalQuantity,List<String> productNames) {}
    public record Response(Long id,Long productId,String productName,String baseUnit,long targetQuantity,LocalDate plannedDate,
                           String batchNo,ProductionTask.Status status,String remarks,long completedQuantity,int progressPercent,
                           int issueMovementCount,int returnMovementCount,List<MovementResponse> movements,Instant createdAt,Instant updatedAt,
                           List<TargetResponse> targets) {}

    @Transactional(readOnly=true) public List<Response> list(ProductionTask.Status status){return tasks.search(status).stream().map(this::response).toList();}
    @Transactional(readOnly=true) public Response detail(long id){return response(get(id));}
    @Transactional public Response create(SaveRequest r){ProductionTask t=new ProductionTask();apply(t,r,false);return response(tasks.saveAndFlush(t));}
    @Transactional public Response update(long id,SaveRequest r){ProductionTask t=get(id);apply(t,r,true);return response(t);}
    @Transactional public Response status(long id,ProductionTask.Status status){ProductionTask t=get(id);t.setStatus(status);return response(t);}
    @Transactional(readOnly=true) public ProductionTask getLinkable(long id){ProductionTask t=get(id);if(t.getStatus()==ProductionTask.Status.COMPLETED||t.getStatus()==ProductionTask.Status.CANCELLED)throw BusinessException.badRequest("PRODUCTION_TASK_CLOSED","该生产任务已结束，不能继续关联流转记录。");return t;}

    private ProductionTask get(long id){return tasks.findWithProductById(id).orElseThrow(()->BusinessException.notFound("PRODUCTION_TASK_NOT_FOUND","找不到该生产任务。"));}
    private void apply(ProductionTask t,SaveRequest r,boolean editing){
        List<TargetRequest> inputs=r.targets();
        if(inputs==null&&r.productId()!=null&&r.targetQuantity()!=null)
            inputs=List.of(new TargetRequest(r.productId(),r.targetQuantity()));
        if(inputs==null||inputs.isEmpty())throw BusinessException.badRequest("TASK_TARGET_REQUIRED","请至少添加一个目标产品。");
        Set<Long> seen=new HashSet<>();
        Map<Long,ProductionTaskTarget> existing=new HashMap<>();
        t.getTargets().forEach(target->existing.put(target.getProduct().getId(),target));
        List<ProductionTaskTarget> updated=new ArrayList<>();
        for(TargetRequest input:inputs){
            if(input==null||input.productId()==null||input.targetQuantity()<=0)
                throw BusinessException.badRequest("TASK_TARGET_INVALID","每个目标产品都必须填写大于零的目标数量。");
            if(!seen.add(input.productId()))throw BusinessException.badRequest("TASK_TARGET_DUPLICATE","同一个目标产品不能重复添加。");
            ProductionTaskTarget target=existing.get(input.productId());
            Product product=products.getForMovement(input.productId(),ProductUsage.WAREHOUSE_TO_PRODUCTION,editing&&target!=null);
            if(target==null){target=new ProductionTaskTarget();target.setTask(t);}
            target.setProduct(product);target.setTargetQuantity(input.targetQuantity());target.setSortOrder(updated.size());updated.add(target);
        }
        t.getTargets().clear();t.getTargets().addAll(updated);
        // Keep the original columns aligned with the first target for compatibility.
        t.setProduct(updated.getFirst().getProduct());t.setTargetQuantity(updated.getFirst().getTargetQuantity());
        t.setPlannedDate(r.plannedDate());t.setBatchNo(r.batchNo().trim());t.setRemarks(blank(r.remarks()));
    }
    private Response response(ProductionTask t){
        List<Movement> rows=movements.findByProductionTaskIdOrderByMovementTimeAsc(t.getId());
        Map<Long,Long> issuedByProduct=new HashMap<>(),returnedByProduct=new HashMap<>();
        Map<Long,Integer> unknownByProduct=new HashMap<>();
        for(Movement movement:rows){
            if(movement.getStatus()!=MovementEnums.Status.ACTIVE)continue;
            boolean issue=movement.getDirection()==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION;
            boolean returned=movement.getDirection()==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&movement.isReturnMovement();
            if(!issue&&!returned)continue;
            for(MovementItem item:movement.getItems()){
                Long productId=item.getProduct().getId();
                if(item.getTotalUnits()==null||item.isQuantityUnknown())unknownByProduct.merge(productId,1,Integer::sum);
                else (issue?issuedByProduct:returnedByProduct).merge(productId,item.getTotalUnits(),Long::sum);
            }
        }
        List<TargetResponse> targets=t.getTargets().stream().map(target->{
            Product p=target.getProduct();
            long issued=issuedByProduct.getOrDefault(p.getId(),0L),returned=returnedByProduct.getOrDefault(p.getId(),0L);
            long completed=issued-returned;
            boolean material=p.getMovementDirection()==ProductUsage.WAREHOUSE_TO_PRODUCTION;
            return new TargetResponse(p.getId(),p.getName(),p.getMaterialCode(),p.getBaseUnit(),target.getTargetQuantity(),completed,
                    material?(int)Math.max(0,Math.min(100,completed*100.0/target.getTargetQuantity())):0,
                    issued,returned,Math.max(0,target.getTargetQuantity()-completed),unknownByProduct.getOrDefault(p.getId(),0),material);
        }).toList();
        // Material readiness is independent of finished-goods receipts and production status.
        int progress=(int)targets.stream().mapToDouble(target->target.materialTarget()?Math.max(0,Math.min(100,target.completedQuantity()*100.0/target.targetQuantity())):0).average().orElse(0);
        long completed=targets.isEmpty()?0:targets.getFirst().completedQuantity();
        int issues=(int)rows.stream().filter(m->m.getItems().stream().anyMatch(i->!i.getIssues().isEmpty())).count();
        int returns=(int)rows.stream().filter(Movement::isReturnMovement).count();
        List<MovementResponse> linked=rows.stream().map(m->new MovementResponse(m.getId(),m.getRecordNo(),m.getDirection(),m.isReturnMovement(),m.getMovementTime(),m.getTotalCartons(),m.getItems().stream().map(MovementItem::getTotalUnits).filter(Objects::nonNull).mapToLong(Long::longValue).sum(),m.getItems().stream().map(MovementItem::getProductNameSnapshot).distinct().toList())).toList();
        return new Response(t.getId(),t.getProduct().getId(),t.displayName(),t.getProduct().getBaseUnit(),t.getTargetQuantity(),t.getPlannedDate(),t.getBatchNo(),t.getStatus(),t.getRemarks(),completed,progress,issues,returns,linked,t.getCreatedAt(),t.getUpdatedAt(),targets);
    }
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
}
