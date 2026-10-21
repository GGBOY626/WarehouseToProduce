package nz.co.warehouse.movement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.co.warehouse.common.BusinessException;
import nz.co.warehouse.person.*;
import nz.co.warehouse.product.*;
import nz.co.warehouse.production.ProductionTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.*;
import java.io.IOException;
import java.time.*;
import java.util.*;

@Slf4j @Service @RequiredArgsConstructor
public class MovementService {
    private final MovementRepository repository;
    private final ProductService products;
    private final PersonService persons;
    private final RecordNumberService numbers;
    private final TransactionTemplate transactions;
    private final ProductionTaskService productionTasks;
    @Value("${app.business-zone:Pacific/Auckland}") private String zone;

    public MovementDtos.DetailResponse create(MovementDtos.SaveRequest request) {
        var existing=repository.findByIdempotencyKey(request.idempotencyKey());
        if(existing.isPresent()){log.info("幂等重复请求: {}",request.idempotencyKey());return detailInTransaction(existing.get().getId());}
        try {
            return transactions.execute(status->{
                Movement m=new Movement();
                m.setIdempotencyKey(request.idempotencyKey());
                m.setRecordNo(numbers.next(request.movementTime()));
                apply(m,request);
                return MovementDtos.detail(repository.saveAndFlush(m));
            });
        } catch(DataIntegrityViolationException ex) {
            return repository.findByIdempotencyKey(request.idempotencyKey()).map(x->detailInTransaction(x.getId())).orElseThrow(()->ex);
        }
    }

    @Transactional
    public MovementDtos.DetailResponse update(long id, MovementDtos.SaveRequest request) {
        Movement m=getActiveDetail(id); apply(m,request); return MovementDtos.detail(repository.saveAndFlush(m));
    }

    @Transactional
    public MovementDtos.DetailResponse linkTask(long id,long taskId) {
        Movement m=getActiveDetail(id);
        if(m.getProductionTask()!=null)throw BusinessException.badRequest("TASK_ALREADY_LINKED","记录已关联任务，请刷新后查看。");
        var selection=m.getItems().stream().map(i->new ProductionTaskService.BatchSelection(i.getProduct().canonicalId(),i.effectiveMaterialBatch())).toList();
        var task=productionTasks.getLinkableBatches(taskId,selection,m.getDirection(),m.isReturnMovement());
        if(m.getDirection()==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&!m.isReturnMovement()&&task.outputCartonUnits()!=null&&m.getItems().stream().anyMatch(i->i.getFullCartons()>0&&!Objects.equals(task.outputCartonUnits(),i.getUnitsPerCartonSnapshot())))
            throw BusinessException.badRequest("TASK_CARTON_SPEC_MISMATCH","满箱规格与任务包装设置不一致，请核对每箱盒数和每盒数量。");
        m.setProductionTask(task);
        return MovementDtos.detail(repository.saveAndFlush(m));
    }

    @Transactional
    public void delete(long id) {
        Movement movement=repository.findDetailById(id).orElseThrow(()->BusinessException.notFound("MOVEMENT_NOT_FOUND","找不到该流转记录。"));
        List<String> photoPaths=movement.getPhotos().stream().map(MovementPhoto::getFilePath).toList();
        repository.delete(movement);
        repository.flush();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { photoPaths.forEach(MovementService.this::deletePhotoFile); }
        });
    }

    @Transactional(readOnly=true)
    public MovementDtos.DetailResponse detail(long id){return MovementDtos.detail(repository.findDetailById(id).orElseThrow(()->BusinessException.notFound("MOVEMENT_NOT_FOUND","找不到该流转记录。")));}

    private MovementDtos.DetailResponse detailInTransaction(long id){return transactions.execute(status->MovementDtos.detail(repository.findDetailById(id).orElseThrow(()->BusinessException.notFound("MOVEMENT_NOT_FOUND","找不到该流转记录。"))));}

    @Transactional(readOnly=true)
    public MovementDtos.PageResponse search(LocalDate from,LocalDate to,MovementEnums.Direction direction,MovementEnums.Status status,Boolean missingPhoto,Boolean hasIssue,String q,int page,int size){
        return search(from,to,direction,status,missingPhoto,hasIssue,q,page,size,null);
    }

    @Transactional(readOnly=true)
    public MovementDtos.PageResponse search(LocalDate from,LocalDate to,MovementEnums.Direction direction,MovementEnums.Status status,Boolean missingPhoto,Boolean hasIssue,String q,int page,int size,Boolean finished){
        ZoneId z=ZoneId.of(zone);Instant start=from.atStartOfDay(z).toInstant();Instant end=to.plusDays(1).atStartOfDay(z).toInstant();
        Page<Movement> result=repository.search(start,end,direction,status,missingPhoto,hasIssue,q==null?"":q.trim(),finished,PageRequest.of(page,Math.min(size,100),Sort.by(Sort.Direction.DESC,"movementTime")));
        return new MovementDtos.PageResponse(result.getContent().stream().map(MovementDtos::summary).toList(),page,result.getSize(),result.getTotalElements(),result.getTotalPages());
    }

    @Transactional(readOnly=true)
    public MovementDtos.TodayStatsResponse todayStats(LocalDate date,MovementEnums.Direction direction){
        ZoneId z=ZoneId.of(zone);Instant start=date.atStartOfDay(z).toInstant();Instant end=date.plusDays(1).atStartOfDay(z).toInstant();
        List<MovementDtos.ProductStatResponse> productStats=productStats(repository.findStatisticItems(start,end,direction,MovementEnums.Status.ACTIVE,null,null,"",null),false);
        long cartons=productStats.stream().mapToLong(MovementDtos.ProductStatResponse::totalCartons).sum();
        long quantity=productStats.stream().mapToLong(MovementDtos.ProductStatResponse::totalQuantity).sum();
        long unknown=productStats.stream().mapToLong(MovementDtos.ProductStatResponse::unknownItemCount).sum();
        return new MovementDtos.TodayStatsResponse(cartons,quantity,unknown,productStats);
    }

    @Transactional(readOnly=true)
    public MovementDtos.QueryStatsResponse queryStats(LocalDate from,LocalDate to,MovementEnums.Direction direction,MovementEnums.Status status,Boolean missingPhoto,Boolean hasIssue,String q){
        return queryStats(from,to,direction,status,missingPhoto,hasIssue,q,null);
    }

    @Transactional(readOnly=true)
    public MovementDtos.QueryStatsResponse queryStats(LocalDate from,LocalDate to,MovementEnums.Direction direction,MovementEnums.Status status,Boolean missingPhoto,Boolean hasIssue,String q,Boolean finished){
        ZoneId z=ZoneId.of(zone);Instant start=from.atStartOfDay(z).toInstant();Instant end=to.plusDays(1).atStartOfDay(z).toInstant();
        Map<MovementEnums.Direction,List<MovementItem>> sources=new EnumMap<>(MovementEnums.Direction.class);
        repository.findStatisticItems(start,end,direction,status,missingPhoto,hasIssue,q==null?"":q.trim(),finished)
                .forEach(item->sources.computeIfAbsent(item.getMovement().getDirection(),ignored->new ArrayList<>()).add(item));
        Map<MovementEnums.Direction,List<MovementDtos.ProductStatResponse>> grouped=new EnumMap<>(MovementEnums.Direction.class);
        sources.forEach((key,items)->grouped.put(key,productStats(items,true)));
        List<MovementDtos.DirectionStatResponse> directions=grouped.entrySet().stream().map(entry->{
            long cartons=entry.getValue().stream().mapToLong(MovementDtos.ProductStatResponse::totalCartons).sum();
            long quantity=entry.getValue().stream().mapToLong(MovementDtos.ProductStatResponse::totalQuantity).sum();
            long unknown=entry.getValue().stream().mapToLong(MovementDtos.ProductStatResponse::unknownItemCount).sum();
            return new MovementDtos.DirectionStatResponse(entry.getKey(),cartons,quantity,unknown,entry.getValue());
        }).toList();
        return new MovementDtos.QueryStatsResponse(directions);
    }

    private List<MovementDtos.ProductStatResponse> productStats(List<MovementItem> items,boolean includeSources){
        return MaterialQuantity.group(items).entrySet().stream().map(entry->{
            var key=entry.getKey();var totals=MaterialQuantity.of(entry.getValue());
            Map<Long,List<MovementItem>> records=new LinkedHashMap<>();
            if(includeSources)entry.getValue().forEach(item->records.computeIfAbsent(item.getMovement().getId(),ignored->new ArrayList<>()).add(item));
            List<MovementDtos.StatMovementResponse> movements=records.values().stream().map(recordItems->{
                Movement m=recordItems.getFirst().getMovement();var quantity=MaterialQuantity.of(recordItems);
                return new MovementDtos.StatMovementResponse(m.getId(),m.getRecordNo(),m.getMovementTime(),m.getDirection(),m.getSenderNameSnapshot(),m.getReceiverNameSnapshot(),
                        quantity.fullCartons(),quantity.totalQuantity(),quantity.unknownItemCount(),quantity.looseUnits(),m.isReturnMovement(),m.getPhotos().size(),quantity.tailCartons(),quantity.totalCartons(),quantity.packaging());
            }).toList();
            return new MovementDtos.ProductStatResponse(entry.getValue().getFirst().getProductNameSnapshot(),key.materialCode(),key.materialBatch(),key.baseUnit(),
                    totals.fullCartons(),totals.looseUnits(),totals.totalQuantity(),totals.unknownItemCount(),movements,totals.tailCartons(),totals.totalCartons(),totals.packaging(),key.subBatch());
        }).toList();
    }

    private Person resolvePerson(Long requestedId,Person existing,String defaultName){
        if(requestedId==null)return existing!=null?existing:persons.getOrCreateDefault(defaultName);
        if(existing!=null&&existing.getId().equals(requestedId))return existing;
        return persons.getActive(requestedId);
    }

    private void apply(Movement m,MovementDtos.SaveRequest r){
        validateDirection(r);
        boolean outbound=r.direction()==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION;
        Person sender=resolvePerson(r.senderPersonId(),m.getSenderPerson(),outbound?"Ray":"xiao");
        Person receiver=resolvePerson(r.receiverPersonId(),m.getReceiverPerson(),outbound?"xiao":"Ray");
        String senderName=m.getSenderPerson()!=null&&m.getSenderPerson().getId().equals(sender.getId())?m.getSenderNameSnapshot():sender.getName();
        String receiverName=m.getReceiverPerson()!=null&&m.getReceiverPerson().getId().equals(receiver.getId())?m.getReceiverNameSnapshot():receiver.getName();
        if(m.getId()==null&&r.senderPersonId()==null)senderName=outbound?"Ray":"xiao";
        if(m.getId()==null&&r.receiverPersonId()==null)receiverName=outbound?"xiao":"Ray";
        var oldSelection=m.getItems().stream().map(i->new ProductionTaskService.BatchSelection(i.getProduct().canonicalId(),i.effectiveMaterialBatch())).collect(java.util.stream.Collectors.toSet());
        boolean keepTask=m.getProductionTask()!=null&&m.getProductionTask().getId().equals(r.productionTaskId())
                &&m.getDirection()==r.direction()&&m.isReturnMovement()==r.returnMovement();
        Map<Long,String> oldBatches=new HashMap<>();
        m.getItems().forEach(i->oldBatches.put(i.getId(),i.effectiveMaterialBatch()));
        m.setDirection(r.direction());m.setReturnMovement(r.returnMovement());m.setFinished(r.direction()==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&!r.returnMovement());m.setMovementTime(r.movementTime());m.setSenderPerson(sender);m.setSenderNameSnapshot(senderName);m.setReceiverPerson(receiver);m.setReceiverNameSnapshot(receiverName);
        m.setRemarks(blank(r.remarks()));
        Set<Long> existingProductIds=m.getItems().stream().map(x->x.getProduct().canonicalId()).collect(java.util.stream.Collectors.toSet());
        Map<Long,MovementItem> originals=new HashMap<>();
        m.getItems().forEach(item->originals.put(item.getId(),item));
        m.getItems().clear();int cartons=0;int order=0;long knownQuantity=0;
        ProductUsage expectedUsage=r.returnMovement()?ProductUsage.WAREHOUSE_TO_PRODUCTION
                :(r.direction()==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION?ProductUsage.WAREHOUSE_TO_PRODUCTION:ProductUsage.PRODUCTION_TO_WAREHOUSE);
        for(var input:r.items()){
            MovementItem original=input.id()==null?null:originals.get(input.id());
            if(input.id()!=null&&original==null)throw BusinessException.badRequest("ITEM_NOT_FOUND","明细不属于当前流转记录，请刷新后重试。");
            if(original!=null&&!original.getProduct().canonicalId().equals(products.getAny(input.productId()).canonicalId()))original=null;
            MovementItem item=buildItem(input,order++,existingProductIds.contains(input.productId()),expectedUsage,original);
            String requestedBatch=input.materialBatch()!=null?input.materialBatch():r.manufactureLot()!=null?r.manufactureLot():original==null?null:oldBatches.get(original.getId());
            boolean unchangedLegacy=original!=null&&Objects.equals(blank(requestedBatch),oldBatches.get(original.getId()));
            String batch=unchangedLegacy?blank(requestedBatch):products.resolveBatch(item.getProduct(),requestedBatch,expectedUsage==ProductUsage.WAREHOUSE_TO_PRODUCTION);
            item.setMaterialBatch(batch==null?"":batch);
            String number=original!=null&&input.productionNumber()==null?original.getProductionNumber()
                    :original!=null&&original.getProductionNumber()==null&&blank(input.productionNumber())==null?null
                    :products.resolveProductionNumber(item.getProduct(),input.productionNumber());
            item.setProductionNumber(number);
            m.addItem(item);
            try{cartons=Math.addExact(cartons,item.getTotalCartons());knownQuantity=Math.addExact(knownQuantity,item.getTotalUnits()==null?0:item.getTotalUnits());}
            catch(ArithmeticException ex){throw BusinessException.badRequest("QUANTITY_OVERFLOW","箱数或数量超出可保存范围。");}
        }
        var selection=m.getItems().stream().map(i->new ProductionTaskService.BatchSelection(i.getProduct().canonicalId(),i.effectiveMaterialBatch())).toList();
        m.setProductionTask(r.productionTaskId()==null?null:keepTask&&oldSelection.equals(new HashSet<>(selection))?m.getProductionTask():productionTasks.getLinkableBatches(r.productionTaskId(),selection,r.direction(),r.returnMovement()));
        if(m.getProductionTask()!=null&&r.direction()==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&!r.returnMovement()){
            Integer plannedUnits=m.getProductionTask().outputCartonUnits();
            if(plannedUnits!=null&&m.getItems().stream().anyMatch(i->i.getFullCartons()>0&&!Objects.equals(plannedUnits,i.getUnitsPerCartonSnapshot())&&
                    (r.items().get(i.getSortOrder()).id()==null||!Objects.equals(originals.get(r.items().get(i.getSortOrder()).id()).getUnitsPerCartonSnapshot(),i.getUnitsPerCartonSnapshot()))))
                throw BusinessException.badRequest("TASK_CARTON_SPEC_MISMATCH","满箱规格与任务包装设置不一致，请核对每箱盒数和每盒数量。");
        }
        m.getItems().forEach(i->products.rememberBatch(i.getProduct(),i.effectiveMaterialBatch()));
        m.getItems().forEach(i->products.rememberProductionNumber(i.getProduct(),i.getProductionNumber()));
        var batches=m.getItems().stream().map(MovementItem::effectiveMaterialBatch).distinct().toList();
        m.setManufactureLot(batches.size()==1?batches.getFirst():null);
        m.setTotalCartons(cartons);
    }

    private MovementItem buildItem(MovementDtos.ItemRequest r,int order,boolean allowInactive,ProductUsage expectedUsage,MovementItem original){
        Product p=products.getForMovement(r.productId(),expectedUsage,allowInactive);
        Integer packedUnits=null;
        Long tailUnits=r.tailUnitsPerCarton();
        boolean boxed=r.boxesPerCarton()!=null||r.unitsPerBox()!=null;
        if(boxed){
            if(r.boxesPerCarton()==null||r.unitsPerBox()==null||r.boxesPerCarton()<=0||r.unitsPerBox()<=0)
                throw BusinessException.badRequest("BOX_PACKING_INVALID","请填写每箱盒数和每盒数量。");
            if("盒".equals(original==null?p.getBaseUnit():original.getBaseUnitSnapshot())&&r.unitsPerBox()!=1)
                throw BusinessException.badRequest("BOX_PACKING_INVALID","按盒计量时，每盒基础数量应为 1。");
            try{packedUnits=Math.multiplyExact(r.boxesPerCarton(),r.unitsPerBox());}
            catch(ArithmeticException ex){throw BusinessException.badRequest("QUANTITY_OVERFLOW","每箱数量超出可保存范围。");}
            if(r.unitsPerCarton()!=null&&!Objects.equals(r.unitsPerCarton(),packedUnits))throw BusinessException.badRequest("BOX_PACKING_INVALID","每箱总数量与包装换算不一致。");
            if(r.tailCartons()>0){
                if(r.tailBoxesPerCarton()==null||r.tailBoxesPerCarton()<=0)throw BusinessException.badRequest("BOX_PACKING_INVALID","请填写每个尾箱实际盒数。");
                tailUnits=(long)r.tailBoxesPerCarton()*r.unitsPerBox();
                if(r.tailUnitsPerCarton()!=null&&!Objects.equals(tailUnits,r.tailUnitsPerCarton()))throw BusinessException.badRequest("BOX_PACKING_INVALID","尾箱数量与盒数换算不一致。");
            }
        }
        if(r.tailBoxesPerCarton()!=null&&(!boxed||r.tailCartons()==0))throw BusinessException.badRequest("BOX_PACKING_INVALID","尾箱盒数需要完整包装规格和尾箱箱数。");
        var specs=p.availableCartonSpecs();
        if(original==null&&r.fullCartons()>0&&packedUnits==null&&r.unitsPerCarton()==null&&specs.size()>1)
            throw BusinessException.badRequest("CARTON_SPEC_REQUIRED","该产品有多种每箱数量，请选择本次完整箱规格。");
        Integer unitsPerCarton=packedUnits!=null?packedUnits:r.unitsPerCarton()!=null?r.unitsPerCarton():original!=null?original.getUnitsPerCartonSnapshot():specs.size()==1?specs.iterator().next():null;
        MovementItem i=new MovementItem();i.setProduct(p);i.setProductNameSnapshot(original==null?p.getName():original.getProductNameSnapshot());i.setSkuSnapshot(original==null?null:original.getSkuSnapshot());i.setUnitsPerCartonSnapshot(unitsPerCarton);i.setBatchNo(r.batchNo().trim());i.setSortOrder(order);i.setRemarks(blank(r.remarks()));
        i.setBoxesPerCartonSnapshot(r.boxesPerCarton());i.setUnitsPerBoxSnapshot(r.unitsPerBox());
        // Older clients may omit the factors when editing notes/counts. Keep the
        // snapshot only when their quantities still match that original packing.
        if(!boxed&&original!=null&&original.getUnitsPerBoxSnapshot()!=null&&Objects.equals(unitsPerCarton,original.getUnitsPerCartonSnapshot())&&
                (tailUnits==null||tailUnits%original.getUnitsPerBoxSnapshot()==0)){
            i.setBoxesPerCartonSnapshot(original.getBoxesPerCartonSnapshot());i.setUnitsPerBoxSnapshot(original.getUnitsPerBoxSnapshot());
        }
        String subBatch=r.subBatch()==null&&original!=null?original.getSubBatch():blank(r.subBatch());
        if(subBatch!=null&&subBatch.length()>100)throw BusinessException.badRequest("SUB_BATCH_INVALID","区分批次最多填写 100 个字符。");
        i.setSubBatch(subBatch);
        boolean quantityUnknown=(original==null&&p.isQuantityUnknown())||r.quantityUnknown();
        if(r.fullCartons()<0||r.tailCartons()<0||r.looseUnits()<0||(r.unitsPerCarton()!=null&&r.unitsPerCarton()<=0)||(r.totalUnits()!=null&&r.totalUnits()<0))
            throw BusinessException.badRequest("QUANTITY_INVALID","箱数和数量不能为负数，每箱数量必须为正整数。");
        if((r.tailCartons()>0&&(tailUnits==null||tailUnits<=0))||(r.tailCartons()==0&&tailUnits!=null))
            throw BusinessException.badRequest("TAIL_QUANTITY_INVALID","有尾箱时请填写尾箱箱数及每箱实际数量；没有尾箱时请清空尾箱数量。");
        i.setTailCartons(r.tailCartons());i.setTailUnitsPerCarton(tailUnits);
        if(!quantityUnknown&&r.tailCartons()==0&&!QuantityRules.hasQuantity(r.fullCartons(),r.looseUnits(),r.totalUnits()))throw BusinessException.badRequest("QUANTITY_REQUIRED","每个产品至少填写一种数量，或选择数量不确定。");
        i.setBaseUnitSnapshot(original==null?(p.getBaseUnit()==null?"—":p.getBaseUnit()):original.getBaseUnitSnapshot());i.setFullCartons(r.fullCartons());i.setLooseUnits(r.looseUnits());
        Long calculated;
        try {
            i.getTotalCartons();
            calculated=QuantityRules.calculate(i.getUnitsPerCartonSnapshot(),r.fullCartons(),r.tailCartons(),tailUnits,r.looseUnits());
        } catch(ArithmeticException ex){throw BusinessException.badRequest("QUANTITY_OVERFLOW","数量超出可保存范围。");}
        if(quantityUnknown)calculated=null;
        i.setCalculatedTotalUnits(calculated);i.setTotalUnits(quantityUnknown?null:(r.totalUnits()!=null?r.totalUnits():calculated));i.setTotalUnitsOverridden(!quantityUnknown&&i.getTotalUnits()!=null&&!Objects.equals(i.getTotalUnits(),calculated));i.setQuantityUnknown(quantityUnknown);
        Set<MovementEnums.IssueType> seen=new HashSet<>();for(var issue:Optional.ofNullable(r.issues()).orElse(List.of())){if(!seen.add(issue.type()))throw BusinessException.badRequest("DUPLICATE_ISSUE","同一种异常不能重复选择。");if(issue.type()==MovementEnums.IssueType.OTHER&&(issue.description()==null||issue.description().isBlank()))throw BusinessException.badRequest("ISSUE_DESCRIPTION_REQUIRED","选择“其他”异常时必须填写说明。");MovementItemIssue entity=new MovementItemIssue();entity.setIssueType(issue.type());entity.setDescription(blank(issue.description()));i.addIssue(entity);}return i;
    }
    private void validateDirection(MovementDtos.SaveRequest r){
        if(r.returnMovement()&&r.direction()!=MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE)
            throw BusinessException.badRequest("RETURN_DIRECTION_INVALID","退回只能用于生产车间到仓库的记录。");

    }
    private Movement getActiveDetail(long id){Movement m=repository.findDetailById(id).orElseThrow(()->BusinessException.notFound("MOVEMENT_NOT_FOUND","找不到该流转记录。"));if(m.getStatus()==MovementEnums.Status.VOID)throw new BusinessException("MOVEMENT_VOID","已作废记录不能修改。",HttpStatus.CONFLICT);return m;}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    @Value("${app.upload-dir:./data/uploads}") private String uploadDir;
    private void deletePhotoFile(String relative) {
        try {
            Path root=Path.of(uploadDir).toAbsolutePath().normalize();
            Path path=root.resolve(relative).normalize();
            if(path.startsWith(root))Files.deleteIfExists(path);
        } catch(IOException ex) { log.error("删除流转记录后清理照片失败: {}",relative,ex); }
    }
}
