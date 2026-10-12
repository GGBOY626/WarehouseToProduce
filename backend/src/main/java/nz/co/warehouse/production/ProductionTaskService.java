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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service @RequiredArgsConstructor
public class ProductionTaskService {
    private final ProductionTaskRepository tasks;
    private final MovementRepository movements;
    private final ProductService products;

    public record TargetRequest(@NotNull Long productId,@Positive long targetQuantity,@Size(max=100) String materialBatch) {
        public TargetRequest(Long productId,long targetQuantity){this(productId,targetQuantity,null);}
    }
    public record BatchSelection(@NotNull Long productId,@Size(max=100) String materialBatch) {
        public BatchSelection { materialBatch=materialBatch==null||materialBatch.isBlank()?null:materialBatch.trim(); }
    }
    public record LinkRequest(@NotEmpty List<@Valid BatchSelection> items,@NotNull MovementEnums.Direction direction,boolean returnMovement) {}
    public record SaveRequest(Long productId,Long targetQuantity,@NotNull LocalDate plannedDate,
                              @NotBlank @Size(max=100) String batchNo,@Size(max=1000) String remarks,
                              List<@NotNull @Valid TargetRequest> targets, Long outputProductId, @Positive Long targetOutputQuantity, @Size(max=100) String name,@Size(max=100) String outputMaterialBatch) {
        public SaveRequest(Long productId,Long targetQuantity,LocalDate plannedDate,String batchNo,String remarks,List<TargetRequest> targets,Long outputProductId,Long targetOutputQuantity,String name){
            this(productId,targetQuantity,plannedDate,batchNo,remarks,targets,outputProductId,targetOutputQuantity,name,null);
        }
        public SaveRequest(Long productId,Long targetQuantity,LocalDate plannedDate,String batchNo,String remarks,List<TargetRequest> targets,Long outputProductId,Long targetOutputQuantity){
            this(productId,targetQuantity,plannedDate,batchNo,remarks,targets,outputProductId,targetOutputQuantity,null);
        }
        public SaveRequest(Long productId,Long targetQuantity,LocalDate plannedDate,String batchNo,String remarks,List<TargetRequest> targets){
            this(productId,targetQuantity,plannedDate,batchNo,remarks,targets,null,null);
        }
        public SaveRequest(Long productId,long targetQuantity,LocalDate plannedDate,String batchNo,String remarks){
            this(productId,targetQuantity,plannedDate,batchNo,remarks,null);
        }
    }
    public record TargetResponse(Long productId,String productName,String materialCode,String baseUnit,long targetQuantity,
                                 long completedQuantity,int progressPercent,long issuedQuantity,long returnedQuantity,
                                 long shortageQuantity,int unknownQuantityCount,boolean materialTarget,BigDecimal estimatedUsedQuantity,String materialBatch) {}
    public record MovementResponse(Long id,String recordNo,MovementEnums.Direction direction,boolean returnMovement,
                                   Instant movementTime,int totalCartons,long totalQuantity,List<String> productNames,MovementEnums.Status status,int unknownQuantityCount,String quantityUnit) {}
    public record Response(Long id,Long productId,String productName,String baseUnit,long targetQuantity,LocalDate plannedDate,
                           String batchNo,ProductionTask.Status status,String remarks,long completedQuantity,int progressPercent,
                           int issueMovementCount,int returnMovementCount,List<MovementResponse> movements,Instant createdAt,Instant updatedAt,
                           List<TargetResponse> targets,OutputResponse output,String name) {}
    public record OutputResponse(Long productId,String productName,String materialCode,String baseUnit,long targetQuantity,
                                 long receivedQuantity,int progressPercent,int unknownQuantityCount,String materialBatch) {}
    public record LinkOption(Long id,String productName,String batchNo,ProductionTask.Status status,String name) {}

    @Transactional(readOnly=true) public List<Response> list(ProductionTask.Status status){return tasks.search(status).stream().map(this::response).toList();}
    @Transactional(readOnly=true) public Response detail(long id){return response(get(id));}
    @Transactional public Response create(SaveRequest r){ProductionTask t=new ProductionTask();apply(t,r,false);return response(tasks.saveAndFlush(t));}
    @Transactional public Response update(long id,SaveRequest r){ProductionTask t=get(id);apply(t,r,true);return response(t);}
    @Transactional public Response status(long id,ProductionTask.Status status){ProductionTask t=get(id);t.setStatus(status);return response(t);}
    @Transactional(readOnly=true)
    public List<LinkOption> linkable(List<Long> productIds,MovementEnums.Direction direction,boolean returned){
        Set<Long> selected=new HashSet<>(productIds);
        if(selected.isEmpty()||selected.contains(null))return List.of();
        return tasks.search(null).stream().filter(t->linkStatusAllowed(t,direction)&&matches(t,selected,direction,returned))
                .map(t->new LinkOption(t.getId(),t.displayName(),t.getBatchNo(),t.getStatus(),t.displayName())).toList();
    }
    @Transactional(readOnly=true)
    public ProductionTask getLinkable(long id,List<Long> productIds,MovementEnums.Direction direction,boolean returned){
        ProductionTask t=get(id);
        if(!linkStatusAllowed(t,direction))
            throw BusinessException.badRequest("PRODUCTION_TASK_CLOSED","该生产任务已结束或不在备料中，不能关联本次流转。");
        if(!matches(t,new HashSet<>(productIds),direction,returned))
            throw BusinessException.badRequest("PRODUCTION_TASK_PRODUCT_MISMATCH","所选产品与任务不匹配，请选择需要这些物料的任务；成品回仓须先设置任务的成品产品。");
        return t;
    }
    @Transactional(readOnly=true)
    public List<LinkOption> linkableBatches(LinkRequest r){
        if(r.items()==null||r.items().isEmpty())return List.of();
        return tasks.search(null).stream().filter(t->linkStatusAllowed(t,r.direction())&&matchesBatches(t,r.items(),r.direction(),r.returnMovement()))
                .map(t->new LinkOption(t.getId(),t.displayName(),t.getBatchNo(),t.getStatus(),t.displayName())).toList();
    }
    public ProductionTask getLinkableBatches(long id,List<BatchSelection> items,MovementEnums.Direction direction,boolean returned){
        ProductionTask t=get(id);
        if(!linkStatusAllowed(t,direction))throw BusinessException.badRequest("PRODUCTION_TASK_CLOSED","该生产任务已结束或不在备料中，不能关联本次流转。");
        if(!matchesBatches(t,items,direction,returned))throw BusinessException.badRequest("PRODUCTION_TASK_PRODUCT_MISMATCH","所选产品或物料批次与任务不匹配。");
        return t;
    }
    private boolean batchMatches(String required,String actual){return blank(required)==null||Objects.equals(blank(required),blank(actual));}
    private boolean matchesBatches(ProductionTask t,List<BatchSelection> items,MovementEnums.Direction direction,boolean returned){
        if(items.isEmpty())return false;
        return items.stream().allMatch(i->{
            if(i.productId()==null)return false;
            Long id=products.getAny(i.productId()).canonicalId();
            if(direction==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&!returned)
                return t.getOutputProduct()!=null&&t.getOutputProduct().canonicalId().equals(id)&&batchMatches(t.getOutputMaterialBatch(),i.materialBatch());
            return t.getTargets().stream().anyMatch(x->x.getProduct().canonicalId().equals(id)&&batchMatches(x.getMaterialBatch(),i.materialBatch()));
        });
    }

    private boolean linkStatusAllowed(ProductionTask t,MovementEnums.Direction direction){
        if(t.getStatus()==ProductionTask.Status.CANCELLED)return false;
        return direction!=MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION||t.getStatus()==ProductionTask.Status.PREPARING;
    }
    private boolean matches(ProductionTask t,Set<Long> selected,MovementEnums.Direction direction,boolean returned){
        if(selected.isEmpty())return false;
        if(direction==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&!returned)
            return t.getOutputProduct()!=null&&selected.equals(Set.of(t.getOutputProduct().canonicalId()));
        Set<Long> required=new HashSet<>();
        t.getTargets().forEach(x->required.add(x.getProduct().canonicalId()));
        return required.containsAll(selected);
    }

    private ProductionTask get(long id){return tasks.findWithProductById(id).orElseThrow(()->BusinessException.notFound("PRODUCTION_TASK_NOT_FOUND","找不到该生产任务。"));}
    private void apply(ProductionTask t,SaveRequest r,boolean editing){
        if(r.name()!=null){
            String name=r.name().trim();
            if(name.isEmpty()||name.length()>100)throw BusinessException.badRequest("TASK_NAME_INVALID","任务名称请填写 1 至 100 个字符。");
            t.setName(name);
        }
        List<TargetRequest> inputs=r.targets();
        if(inputs==null&&r.productId()!=null&&r.targetQuantity()!=null)
            inputs=List.of(new TargetRequest(r.productId(),r.targetQuantity()));
        if(inputs==null||inputs.isEmpty())throw BusinessException.badRequest("TASK_TARGET_REQUIRED","请至少添加一个目标产品。");
        Set<BatchSelection> seen=new HashSet<>();
        Map<BatchSelection,ProductionTaskTarget> existing=new HashMap<>();
        t.getTargets().forEach(target->existing.put(new BatchSelection(target.getProduct().canonicalId(),target.getMaterialBatch()),target));
        List<ProductionTaskTarget> updated=new ArrayList<>();
        for(TargetRequest input:inputs){
            if(input==null||input.productId()==null||input.targetQuantity()<=0)
                throw BusinessException.badRequest("TASK_TARGET_INVALID","每个目标产品都必须填写大于零的目标数量。");
            Product product=products.getForMovement(input.productId(),ProductUsage.WAREHOUSE_TO_PRODUCTION,
                    editing&&existing.keySet().stream().anyMatch(k->k.productId().equals(input.productId())));
            String batch=blank(input.materialBatch());
            var key=new BatchSelection(product.canonicalId(),batch);
            ProductionTaskTarget target=existing.get(key);
            if(!(editing&&target!=null)) batch=products.resolveBatch(product,batch,false);
            if(!seen.add(key))throw BusinessException.badRequest("TASK_TARGET_DUPLICATE","同一个产品的同一批次不能重复添加。");
            if(target==null){target=new ProductionTaskTarget();target.setTask(t);}
            target.setProduct(product);target.setMaterialBatch(batch==null?"":batch);target.setTargetQuantity(input.targetQuantity());target.setSortOrder(updated.size());updated.add(target);
        }
        for(ProductionTaskTarget target:updated){
            if(blank(target.getMaterialBatch())==null&&updated.stream().filter(x->x.getProduct().canonicalId().equals(target.getProduct().canonicalId())).count()>1)
                throw BusinessException.badRequest("TASK_BATCH_AMBIGUOUS","同一产品添加多行时，请为每行选择具体物料批次，避免重复统计。");
        }
        if((r.outputProductId()==null)!=(r.targetOutputQuantity()==null)||
                (r.targetOutputQuantity()!=null&&r.targetOutputQuantity()<=0))
            throw BusinessException.badRequest("TASK_OUTPUT_INVALID","请同时填写成品产品及大于零的计划产量。");
        // An output identity already used by receipts cannot be replaced: its history must remain meaningful.
        if(editing&&t.getOutputProduct()!=null&&(!Objects.equals(t.getOutputProduct().canonicalId(),r.outputProductId())||!Objects.equals(blank(t.getOutputMaterialBatch()),blank(r.outputMaterialBatch())))&&
                movements.findByProductionTaskIdOrderByMovementTimeAsc(t.getId()).stream().anyMatch(m->
                    m.getDirection()==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&!m.isReturnMovement()&&
                    m.getItems().stream().anyMatch(i->i.getProduct().canonicalId().equals(t.getOutputProduct().canonicalId()))))
            throw BusinessException.badRequest("TASK_OUTPUT_IN_USE","已有成品回仓记录，不能更换或移除成品产品及批次，请新建任务。");
        Product output=r.outputProductId()==null?null:products.getForMovement(r.outputProductId(),ProductUsage.PRODUCTION_TO_WAREHOUSE,
                editing&&t.getOutputProduct()!=null&&t.getOutputProduct().canonicalId().equals(r.outputProductId()));
        if(output!=null&&output.isQuantityUnknown())throw BusinessException.badRequest("TASK_OUTPUT_UNIT_REQUIRED","成品产品须设置明确的数量单位。");
        String outputBatch=output==null?null:editing&&t.getOutputProduct()!=null&&output.canonicalId().equals(t.getOutputProduct().canonicalId())&&Objects.equals(blank(r.outputMaterialBatch()),blank(t.getOutputMaterialBatch()))?blank(r.outputMaterialBatch()):products.resolveBatch(output,r.outputMaterialBatch(),false);
        t.setOutputProduct(output);t.setTargetOutputQuantity(r.targetOutputQuantity());t.setOutputMaterialBatch(outputBatch);
        updated.forEach(x->products.rememberBatch(x.getProduct(),x.getMaterialBatch()));
        if(output!=null)products.rememberBatch(output,outputBatch);
        t.getTargets().clear();t.getTargets().addAll(updated);
        // Keep the original columns aligned with the first target for compatibility.
        t.setProduct(updated.getFirst().getProduct());t.setTargetQuantity(updated.getFirst().getTargetQuantity());
        t.setPlannedDate(r.plannedDate());t.setBatchNo(r.batchNo().trim());t.setRemarks(blank(r.remarks()));
    }
    private Response response(ProductionTask t){
        List<Movement> rows=movements.findByProductionTaskIdOrderByMovementTimeAsc(t.getId());
        long received=0;int unknownOutput=0;
        for(Movement m:rows){
            if(m.getStatus()!=MovementEnums.Status.ACTIVE||m.getDirection()!=MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE||m.isReturnMovement()||t.getOutputProduct()==null)continue;
            for(MovementItem i:m.getItems())if(i.getProduct().canonicalId().equals(t.getOutputProduct().canonicalId())&&batchMatches(t.getOutputMaterialBatch(),i.effectiveMaterialBatch())){
                if(i.isQuantityUnknown()||i.getTotalUnits()==null)unknownOutput++;
                else received=Math.addExact(received,i.getTotalUnits());
            }
        }
        final long outputReceived=received;
        OutputResponse output=t.getOutputProduct()==null?null:new OutputResponse(t.getOutputProduct().canonicalId(),t.getOutputProduct().getName(),
                t.getOutputProduct().getMaterialCode(),t.getOutputProduct().getBaseUnit(),t.getTargetOutputQuantity(),received,
                (int)Math.min(100,received*100.0/t.getTargetOutputQuantity()),unknownOutput,t.getOutputMaterialBatch());
        List<TargetResponse> targets=t.getTargets().stream().map(target->{
            Product p=target.getProduct().canonical();
            long issued=0,returned=0;int unknown=0;
            for(Movement movement:rows){
                if(movement.getStatus()!=MovementEnums.Status.ACTIVE)continue;
                boolean issue=movement.getDirection()==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION;
                boolean isReturn=movement.getDirection()==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&movement.isReturnMovement();
                if(!issue&&!isReturn)continue;
                for(MovementItem item:movement.getItems())if(targetMatches(t,target,item)){
                    if(item.getTotalUnits()==null||item.isQuantityUnknown())unknown++;
                    else if(issue)issued=Math.addExact(issued,item.getTotalUnits());
                    else returned=Math.addExact(returned,item.getTotalUnits());
                }
            }
            long completed=issued-returned;
            boolean material=p.getMovementDirection()==ProductUsage.WAREHOUSE_TO_PRODUCTION;
            return new TargetResponse(p.getId(),p.getName(),p.getMaterialCode(),p.getBaseUnit(),target.getTargetQuantity(),completed,
                    material?(int)Math.max(0,Math.min(100,completed*100.0/target.getTargetQuantity())):0,
                    issued,returned,Math.max(0,target.getTargetQuantity()-completed),unknown,material,output==null||!material?null:BigDecimal.valueOf(outputReceived).multiply(BigDecimal.valueOf(target.getTargetQuantity()))
                            .divide(BigDecimal.valueOf(t.getTargetOutputQuantity()),6,RoundingMode.HALF_UP),blank(target.getMaterialBatch()));
        }).toList();
        // Material readiness is independent of finished-goods receipts and production status.
        int progress=(int)targets.stream().mapToDouble(target->target.materialTarget()?Math.max(0,Math.min(100,target.completedQuantity()*100.0/target.targetQuantity())):0).average().orElse(0);
        long completed=targets.isEmpty()?0:targets.getFirst().completedQuantity();
        int issues=(int)rows.stream().filter(m->m.getItems().stream().anyMatch(i->!i.getIssues().isEmpty())).count();
        int returns=(int)rows.stream().filter(Movement::isReturnMovement).count();
        List<MovementResponse> linked=rows.stream().map(m->new MovementResponse(m.getId(),m.getRecordNo(),m.getDirection(),m.isReturnMovement(),m.getMovementTime(),m.getTotalCartons(),m.getItems().stream().map(MovementItem::getTotalUnits).filter(Objects::nonNull).mapToLong(Long::longValue).sum(),m.getItems().stream().map(MovementItem::getProductNameSnapshot).distinct().toList(),m.getStatus(),(int)m.getItems().stream().filter(i->i.isQuantityUnknown()||i.getTotalUnits()==null).count(),m.getItems().stream().map(MovementItem::getBaseUnitSnapshot).distinct().count()==1?m.getItems().getFirst().getBaseUnitSnapshot():null)).toList();
        return new Response(t.getId(),t.getProduct().canonicalId(),t.displayName(),t.getProduct().getBaseUnit(),t.getTargetQuantity(),t.getPlannedDate(),t.getBatchNo(),t.getStatus(),t.getRemarks(),completed,progress,issues,returns,linked,t.getCreatedAt(),t.getUpdatedAt(),targets,output,t.displayName());
    }
    private boolean targetMatches(ProductionTask task,ProductionTaskTarget target,MovementItem item){
        if(!target.getProduct().canonicalId().equals(item.getProduct().canonicalId())||!batchMatches(target.getMaterialBatch(),item.effectiveMaterialBatch()))return false;
        // Legacy tasks can contain two source products now represented by one canonical product.
        // Keep their old unscoped targets attributed to their original foreign keys, not counted twice.
        if(blank(target.getMaterialBatch())==null&&task.getTargets().stream().filter(x->blank(x.getMaterialBatch())==null&&x.getProduct().canonicalId().equals(target.getProduct().canonicalId())).count()>1)
            return target.getProduct().getId().equals(item.getProduct().getId());
        return true;
    }
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
}
