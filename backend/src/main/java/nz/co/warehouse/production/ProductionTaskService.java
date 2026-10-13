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
                              List<@NotNull @Valid TargetRequest> targets, Long outputProductId, @Positive Long targetOutputQuantity, @Size(max=100) String name,@Size(max=100) String outputMaterialBatch,
                              @Positive Integer outputBoxesPerCarton,@Positive Integer outputUnitsPerBox,
                              ProductionTask.OutputQuantityBasis outputQuantityBasis,Long primaryMaterialProductId) {
        public SaveRequest(Long productId,Long targetQuantity,LocalDate plannedDate,String batchNo,String remarks,List<TargetRequest> targets,Long outputProductId,Long targetOutputQuantity,String name,String outputMaterialBatch){
            this(productId,targetQuantity,plannedDate,batchNo,remarks,targets,outputProductId,targetOutputQuantity,name,outputMaterialBatch,null,null,null,null);
        }
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
                                 long shortageQuantity,int unknownQuantityCount,boolean materialTarget,BigDecimal estimatedUsedQuantity,String materialBatch,List<BatchResponse> batches) {}
    public record BatchResponse(String materialBatch,String subBatch,long issuedQuantity,long returnedQuantity,long netQuantity,int unknownQuantityCount) {}
    public record MovementResponse(Long id,String recordNo,MovementEnums.Direction direction,boolean returnMovement,
                                   Instant movementTime,int totalCartons,long totalQuantity,List<String> productNames,MovementEnums.Status status,int unknownQuantityCount,String quantityUnit) {}
    public record Response(Long id,Long productId,String productName,String baseUnit,long targetQuantity,LocalDate plannedDate,
                           String batchNo,ProductionTask.Status status,String remarks,long completedQuantity,int progressPercent,
                           int issueMovementCount,int returnMovementCount,List<MovementResponse> movements,Instant createdAt,Instant updatedAt,
                           List<TargetResponse> targets,OutputResponse output,String name) {}
    public record OutputResponse(Long productId,String productName,String materialCode,String baseUnit,long targetQuantity,
                                 long receivedQuantity,int progressPercent,int unknownQuantityCount,String materialBatch,
                                 Integer boxesPerCarton,Integer unitsPerBox,ProductionTask.OutputQuantityBasis quantityBasis,
                                 Long primaryMaterialProductId,String primaryMaterialName,String primaryMaterialUnit,Long netIssuedQuantity,
                                 BigDecimal producedQuantity,BigDecimal receivedBoxes,BigDecimal productionPercent,BigDecimal lossPercent,boolean provisional) {}
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
            throw BusinessException.badRequest("PRODUCTION_TASK_CLOSED","该生产任务已结束，不能新增发料。");
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
        if(!linkStatusAllowed(t,direction))throw BusinessException.badRequest("PRODUCTION_TASK_CLOSED","该生产任务已结束，不能新增发料。");
        if(!matchesBatches(t,items,direction,returned))throw BusinessException.badRequest("PRODUCTION_TASK_PRODUCT_MISMATCH","所选产品或物料批次与任务不匹配。");
        return t;
    }
    private boolean matchesBatches(ProductionTask t,List<BatchSelection> items,MovementEnums.Direction direction,boolean returned){
        if(items.isEmpty())return false;
        return items.stream().allMatch(i->{
            if(i.productId()==null)return false;
            Long id=products.getAny(i.productId()).canonicalId();
            if(direction==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&!returned)
                return t.getOutputProduct()!=null&&t.getOutputProduct().canonicalId().equals(id);
            return t.getTargets().stream().anyMatch(x->x.getProduct().canonicalId().equals(id));
        });
    }

    private boolean linkStatusAllowed(ProductionTask t,MovementEnums.Direction direction){
        if(t.getStatus()==ProductionTask.Status.CANCELLED)return false;
        return direction!=MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION||t.getStatus()==ProductionTask.Status.PREPARING||t.getStatus()==ProductionTask.Status.IN_PROGRESS;
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
        Map<Long,Long> quantities=new LinkedHashMap<>();
        for(TargetRequest input:inputs){
            if(input==null||input.productId()==null||input.targetQuantity()<=0)
                throw BusinessException.badRequest("TASK_TARGET_INVALID","每个物料须填写大于零的需求总数。");
            long productId=products.getAny(input.productId()).canonicalId();
            if(!seen.add(new BatchSelection(productId,input.materialBatch())))throw BusinessException.badRequest("TASK_TARGET_DUPLICATE","同一物料请合计填写，不要重复添加。");
            try { quantities.merge(productId,input.targetQuantity(),Math::addExact); }
            catch(ArithmeticException ex){throw BusinessException.badRequest("TASK_QUANTITY_OVERFLOW","需求数量超出范围。");}
        }
        List<ProductionTaskTarget> updated=new ArrayList<>();
        for(var input:quantities.entrySet()){
            var candidates=t.getTargets().stream().filter(x->x.getProduct().canonicalId().equals(input.getKey())).toList();
            ProductionTaskTarget target=candidates.stream().filter(x->x.getProduct().getId().equals(input.getKey())&&blank(x.getMaterialBatch())==null).findFirst().orElse(candidates.isEmpty()?null:candidates.getFirst());
            Product product=products.getForMovement(input.getKey(),ProductUsage.WAREHOUSE_TO_PRODUCTION,editing&&target!=null);
            if(target==null){target=new ProductionTaskTarget();target.setTask(t);}
            target.setProduct(product);target.setMaterialBatch("");target.setTargetQuantity(input.getValue());target.setSortOrder(updated.size());updated.add(target);
        }
        boolean configured=r.outputBoxesPerCarton()!=null||r.outputUnitsPerBox()!=null||r.outputQuantityBasis()!=null||r.primaryMaterialProductId()!=null;
        if(configured&&(r.outputProductId()==null||r.outputBoxesPerCarton()==null||r.outputBoxesPerCarton()<=0||r.outputUnitsPerBox()==null||r.outputUnitsPerBox()<=0||r.outputQuantityBasis()==null||r.primaryMaterialProductId()==null))
            throw BusinessException.badRequest("TASK_PACKAGING_REQUIRED","请填写成品、每箱盒数、每盒数量、入库计量口径及主料。");
        Product primary=configured?products.getAny(r.primaryMaterialProductId()).canonical():null;
        if(primary!=null&&(primary.isQuantityUnknown()||updated.stream().noneMatch(x->x.getProduct().canonicalId().equals(primary.getId()))))
            throw BusinessException.badRequest("TASK_PRIMARY_INVALID","主料必须是本任务所需物料之一，且有明确计量单位。");
        try {if(configured&&r.outputQuantityBasis()==ProductionTask.OutputQuantityBasis.UNIT)Math.multiplyExact(r.outputBoxesPerCarton(),r.outputUnitsPerBox());}
        catch(ArithmeticException ex){throw BusinessException.badRequest("TASK_PACKAGING_OVERFLOW","每箱数量超出可保存范围。");}
        Long plannedOutput=r.targetOutputQuantity();
        if(configured&&plannedOutput==null){
            long primaryTarget=quantities.get(primary.getId());
            plannedOutput=r.outputQuantityBasis()==ProductionTask.OutputQuantityBasis.UNIT?primaryTarget:primaryTarget/r.outputUnitsPerBox()+(primaryTarget%r.outputUnitsPerBox()==0?0:1);
        }
        if((r.outputProductId()==null)!=(plannedOutput==null)||
                (plannedOutput!=null&&plannedOutput<=0))
            throw BusinessException.badRequest("TASK_OUTPUT_INVALID","请同时填写成品产品及大于零的计划产量。");
        // An output identity already used by receipts cannot be replaced: its history must remain meaningful.
        if(editing&&t.getOutputProduct()!=null&&(!Objects.equals(t.getOutputProduct().canonicalId(),r.outputProductId())||t.getOutputBoxesPerCarton()!=null&&(!Objects.equals(t.getOutputBoxesPerCarton(),r.outputBoxesPerCarton())||!Objects.equals(t.getOutputUnitsPerBox(),r.outputUnitsPerBox())||t.getOutputQuantityBasis()!=r.outputQuantityBasis()||!Objects.equals(t.getPrimaryMaterial()==null?null:t.getPrimaryMaterial().canonicalId(),primary==null?null:primary.getId())))&&
                movements.findByProductionTaskIdOrderByMovementTimeAsc(t.getId()).stream().anyMatch(m->
                    m.getDirection()==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&!m.isReturnMovement()&&
                    m.getItems().stream().anyMatch(i->i.getProduct().canonicalId().equals(t.getOutputProduct().canonicalId()))))
            throw BusinessException.badRequest("TASK_OUTPUT_IN_USE","已有成品回仓记录，不能更换成品、包装换算或主料，请新建任务。");
        Product output=r.outputProductId()==null?null:products.getForMovement(r.outputProductId(),ProductUsage.PRODUCTION_TO_WAREHOUSE,
                editing&&t.getOutputProduct()!=null&&t.getOutputProduct().canonicalId().equals(r.outputProductId()));
        if(output!=null&&output.isQuantityUnknown())throw BusinessException.badRequest("TASK_OUTPUT_UNIT_REQUIRED","成品产品须设置明确的数量单位。");
        t.setOutputProduct(output);t.setTargetOutputQuantity(plannedOutput);t.setOutputMaterialBatch(null);
        t.setOutputBoxesPerCarton(r.outputBoxesPerCarton());t.setOutputUnitsPerBox(r.outputUnitsPerBox());
        t.setOutputQuantityBasis(r.outputQuantityBasis());t.setPrimaryMaterial(primary);
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
            for(MovementItem i:m.getItems())if(i.getProduct().canonicalId().equals(t.getOutputProduct().canonicalId())){
                if(i.isQuantityUnknown()||i.getTotalUnits()==null)unknownOutput++;
                else received=Math.addExact(received,i.getTotalUnits());
            }
        }
        final long outputReceived=received;
        Map<Long,List<ProductionTaskTarget>> grouped=new LinkedHashMap<>();
        t.getTargets().forEach(x->grouped.computeIfAbsent(x.getProduct().canonicalId(),ignored->new ArrayList<>()).add(x));
        List<TargetResponse> targets=grouped.entrySet().stream().map(entry->{
            Product p=entry.getValue().getFirst().getProduct().canonical();
            long required=entry.getValue().stream().mapToLong(ProductionTaskTarget::getTargetQuantity).reduce(0L,Math::addExact);
            Map<BatchSelectionKey,long[]> batches=new LinkedHashMap<>();
            long issued=0,returned=0;int unknown=0;
            for(Movement movement:rows){
                if(movement.getStatus()!=MovementEnums.Status.ACTIVE)continue;
                boolean issue=movement.getDirection()==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION;
                boolean isReturn=movement.getDirection()==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&movement.isReturnMovement();
                if(!issue&&!isReturn)continue;
                for(MovementItem item:movement.getItems())if(item.getProduct().canonicalId().equals(p.getId())){
                    long[] batch=batches.computeIfAbsent(new BatchSelectionKey(item.effectiveMaterialBatch(),item.getSubBatch()),ignored->new long[3]);
                    if(item.getTotalUnits()==null||item.isQuantityUnknown()){unknown++;batch[2]++;}
                    else if(issue){issued=Math.addExact(issued,item.getTotalUnits());batch[0]=Math.addExact(batch[0],item.getTotalUnits());}
                    else{ returned=Math.addExact(returned,item.getTotalUnits());batch[1]=Math.addExact(batch[1],item.getTotalUnits());}
                }
            }
            long completed=issued-returned;
            boolean material=p.getMovementDirection()==ProductUsage.WAREHOUSE_TO_PRODUCTION;
            return new TargetResponse(p.getId(),p.getName(),p.getMaterialCode(),p.getBaseUnit(),required,completed,
                    material?(int)Math.max(0,Math.min(100,completed*100.0/required)):0,
                    issued,returned,Math.max(0,required-completed),unknown,material,t.getOutputProduct()==null||!material?null:BigDecimal.valueOf(outputReceived).multiply(BigDecimal.valueOf(required))
                            .divide(BigDecimal.valueOf(t.getTargetOutputQuantity()),6,RoundingMode.HALF_UP),null,
                    batches.entrySet().stream().map(e->new BatchResponse(e.getKey().batch(),e.getKey().subBatch(),e.getValue()[0],e.getValue()[1],e.getValue()[0]-e.getValue()[1],(int)e.getValue()[2])).toList());
        }).toList();
        TargetResponse primary=t.getPrimaryMaterial()==null?null:targets.stream().filter(x->x.productId().equals(t.getPrimaryMaterial().canonicalId())).findFirst().orElse(null);
        BigDecimal produced=t.getOutputQuantityBasis()==null?null:BigDecimal.valueOf(received).multiply(BigDecimal.valueOf(t.getOutputQuantityBasis()==ProductionTask.OutputQuantityBasis.BOX?t.getOutputUnitsPerBox():1));
        BigDecimal boxes=produced==null?null:produced.divide(BigDecimal.valueOf(t.getOutputUnitsPerBox()),6,RoundingMode.HALF_UP);
        boolean ratesKnown=primary!=null&&primary.completedQuantity()>0&&primary.unknownQuantityCount()==0&&unknownOutput==0;
        BigDecimal productionPercent=ratesKnown?produced.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(primary.completedQuantity()),2,RoundingMode.HALF_UP).min(BigDecimal.valueOf(100)):null;
        BigDecimal lossPercent=ratesKnown?BigDecimal.valueOf(100).subtract(productionPercent).max(BigDecimal.ZERO):null;
        OutputResponse output=t.getOutputProduct()==null?null:new OutputResponse(t.getOutputProduct().canonicalId(),t.getOutputProduct().getName(),
                t.getOutputProduct().getMaterialCode(),t.getOutputProduct().getBaseUnit(),t.getTargetOutputQuantity(),received,
                (int)Math.min(100,received*100.0/t.getTargetOutputQuantity()),unknownOutput,null,
                t.getOutputBoxesPerCarton(),t.getOutputUnitsPerBox(),t.getOutputQuantityBasis(),primary==null?null:primary.productId(),primary==null?null:primary.productName(),primary==null?null:primary.baseUnit(),primary==null?null:primary.completedQuantity(),
                produced,boxes,productionPercent,lossPercent,t.getStatus()!=ProductionTask.Status.COMPLETED);
        // Material readiness is independent of finished-goods receipts and production status.
        int progress=(int)targets.stream().mapToDouble(target->target.materialTarget()?Math.max(0,Math.min(100,target.completedQuantity()*100.0/target.targetQuantity())):0).average().orElse(0);
        long completed=targets.isEmpty()?0:targets.getFirst().completedQuantity();
        int issues=(int)rows.stream().filter(m->m.getItems().stream().anyMatch(i->!i.getIssues().isEmpty())).count();
        int returns=(int)rows.stream().filter(Movement::isReturnMovement).count();
        List<MovementResponse> linked=rows.stream().map(m->new MovementResponse(m.getId(),m.getRecordNo(),m.getDirection(),m.isReturnMovement(),m.getMovementTime(),m.getTotalCartons(),m.getItems().stream().map(MovementItem::getTotalUnits).filter(Objects::nonNull).mapToLong(Long::longValue).sum(),m.getItems().stream().map(MovementItem::getProductNameSnapshot).distinct().toList(),m.getStatus(),(int)m.getItems().stream().filter(i->i.isQuantityUnknown()||i.getTotalUnits()==null).count(),m.getItems().stream().map(MovementItem::getBaseUnitSnapshot).distinct().count()==1?m.getItems().getFirst().getBaseUnitSnapshot():null)).toList();
        return new Response(t.getId(),t.getProduct().canonicalId(),t.displayName(),t.getProduct().getBaseUnit(),t.getTargetQuantity(),t.getPlannedDate(),t.getBatchNo(),t.getStatus(),t.getRemarks(),completed,progress,issues,returns,linked,t.getCreatedAt(),t.getUpdatedAt(),targets,output,t.displayName());
    }
    private record BatchSelectionKey(String batch,String subBatch) {}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
}
