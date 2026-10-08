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
    public MovementDtos.DetailResponse voidMovement(long id,String reason) {
        Movement m=getActiveDetail(id);m.setStatus(MovementEnums.Status.VOID);m.setVoidReason(reason.trim());m.setVoidedAt(Instant.now());return MovementDtos.detail(m);
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
        ZoneId z=ZoneId.of(zone);Instant start=from.atStartOfDay(z).toInstant();Instant end=to.plusDays(1).atStartOfDay(z).toInstant();
        Page<Movement> result=repository.search(start,end,direction,status,missingPhoto,hasIssue,q==null?"":q.trim(),PageRequest.of(page,Math.min(size,100),Sort.by(Sort.Direction.DESC,"movementTime")));
        return new MovementDtos.PageResponse(result.getContent().stream().map(MovementDtos::summary).toList(),page,result.getSize(),result.getTotalElements(),result.getTotalPages());
    }

    @Transactional(readOnly=true)
    public MovementDtos.TodayStatsResponse todayStats(LocalDate date,MovementEnums.Direction direction){
        ZoneId z=ZoneId.of(zone);Instant start=date.atStartOfDay(z).toInstant();Instant end=date.plusDays(1).atStartOfDay(z).toInstant();
        List<MovementDtos.ProductStatResponse> productStats=repository.summarizeItems(start,end,direction,MovementEnums.Status.ACTIVE).stream()
                .map(row->new MovementDtos.ProductStatResponse((String)row[0],((Number)row[1]).longValue(),((Number)row[2]).longValue(),((Number)row[3]).longValue())).toList();
        long cartons=productStats.stream().mapToLong(MovementDtos.ProductStatResponse::fullCartons).sum();
        long quantity=productStats.stream().mapToLong(MovementDtos.ProductStatResponse::totalQuantity).sum();
        long unknown=productStats.stream().mapToLong(MovementDtos.ProductStatResponse::unknownItemCount).sum();
        return new MovementDtos.TodayStatsResponse(cartons,quantity,unknown,productStats);
    }

    @Transactional(readOnly=true)
    public MovementDtos.QueryStatsResponse queryStats(LocalDate from,LocalDate to,MovementEnums.Direction direction,MovementEnums.Status status,Boolean missingPhoto,Boolean hasIssue,String q){
        ZoneId z=ZoneId.of(zone);Instant start=from.atStartOfDay(z).toInstant();Instant end=to.plusDays(1).atStartOfDay(z).toInstant();
        Map<MovementEnums.Direction,Map<String,List<MovementDtos.StatMovementResponse>>> sources=new EnumMap<>(MovementEnums.Direction.class);
        repository.summarizeSearch(start,end,direction,status,missingPhoto,hasIssue,q==null?"":q.trim()).forEach(row->{
            MovementEnums.Direction rowDirection=(MovementEnums.Direction)row[0];
            sources.computeIfAbsent(rowDirection,key->new LinkedHashMap<>()).computeIfAbsent((String)row[1],key->new ArrayList<>())
                    .add(new MovementDtos.StatMovementResponse(((Number)row[5]).longValue(),(String)row[6],(Instant)row[7],rowDirection,(String)row[8],(String)row[9],((Number)row[2]).longValue(),((Number)row[3]).longValue(),((Number)row[4]).longValue()));
        });
        Map<MovementEnums.Direction,List<MovementDtos.ProductStatResponse>> grouped=new EnumMap<>(MovementEnums.Direction.class);
        sources.forEach((key,products)->grouped.put(key,products.entrySet().stream().map(entry->new MovementDtos.ProductStatResponse(
                entry.getKey(),entry.getValue().stream().mapToLong(MovementDtos.StatMovementResponse::fullCartons).sum(),
                entry.getValue().stream().mapToLong(MovementDtos.StatMovementResponse::totalQuantity).sum(),
                entry.getValue().stream().mapToLong(MovementDtos.StatMovementResponse::unknownItemCount).sum(),List.copyOf(entry.getValue()))).toList()));
        List<MovementDtos.DirectionStatResponse> directions=grouped.entrySet().stream().map(entry->{
            long cartons=entry.getValue().stream().mapToLong(MovementDtos.ProductStatResponse::fullCartons).sum();
            long quantity=entry.getValue().stream().mapToLong(MovementDtos.ProductStatResponse::totalQuantity).sum();
            long unknown=entry.getValue().stream().mapToLong(MovementDtos.ProductStatResponse::unknownItemCount).sum();
            return new MovementDtos.DirectionStatResponse(entry.getKey(),cartons,quantity,unknown,entry.getValue());
        }).toList();
        return new MovementDtos.QueryStatsResponse(directions);
    }

    private void apply(Movement m,MovementDtos.SaveRequest r){
        validateDirection(r);
        Person sender=m.getId()!=null&&m.getSenderPerson().getId().equals(r.senderPersonId())?persons.getAny(r.senderPersonId()):persons.getActive(r.senderPersonId());
        Person receiver=m.getId()!=null&&m.getReceiverPerson().getId().equals(r.receiverPersonId())?persons.getAny(r.receiverPersonId()):persons.getActive(r.receiverPersonId());
        m.setDirection(r.direction());m.setReturnMovement(r.returnMovement());m.setMovementTime(r.movementTime());m.setSenderPerson(sender);m.setSenderNameSnapshot(sender.getName());m.setReceiverPerson(receiver);m.setReceiverNameSnapshot(receiver.getName());
        m.setProductionTask(r.productionTaskId()==null?null:m.getProductionTask()!=null&&m.getProductionTask().getId().equals(r.productionTaskId())?m.getProductionTask():productionTasks.getLinkable(r.productionTaskId()));
        m.setManufactureLot(r.direction()==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION||r.returnMovement()?r.manufactureLot().trim():null);m.setRemarks(blank(r.remarks()));
        Set<Long> existingProductIds=m.getItems().stream().map(x->x.getProduct().getId()).collect(java.util.stream.Collectors.toSet());
        m.getItems().clear();int cartons=0;int order=0;
        ProductUsage expectedUsage=r.returnMovement()?ProductUsage.WAREHOUSE_TO_PRODUCTION
                :(r.direction()==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION?ProductUsage.WAREHOUSE_TO_PRODUCTION:ProductUsage.PRODUCTION_TO_WAREHOUSE);
        for(var input:r.items()){MovementItem item=buildItem(input,order++,existingProductIds.contains(input.productId()),expectedUsage);m.addItem(item);cartons+=item.getFullCartons();}
        m.setTotalCartons(cartons);
    }

    private MovementItem buildItem(MovementDtos.ItemRequest r,int order,boolean allowInactive,ProductUsage expectedUsage){
        Product p=products.getForMovement(r.productId(),expectedUsage,allowInactive);MovementItem i=new MovementItem();i.setProduct(p);i.setProductNameSnapshot(p.getName());i.setSkuSnapshot(null);i.setUnitsPerCartonSnapshot(p.getDefaultUnitsPerCarton());i.setBatchNo(r.batchNo().trim());i.setSortOrder(order);i.setRemarks(blank(r.remarks()));
        boolean quantityUnknown=p.isQuantityUnknown()||r.quantityUnknown();
        if(!quantityUnknown&&!QuantityRules.hasQuantity(r.fullCartons(),r.looseUnits(),r.totalUnits()))throw BusinessException.badRequest("QUANTITY_REQUIRED","每个产品至少填写一种数量，或选择数量不确定。");
        i.setBaseUnitSnapshot(p.getBaseUnit()==null?"—":p.getBaseUnit());i.setFullCartons(r.fullCartons());i.setLooseUnits(quantityUnknown?0:r.looseUnits());
        Long calculated=quantityUnknown?null:QuantityRules.calculate(p.getDefaultUnitsPerCarton(),r.fullCartons(),r.looseUnits());
        i.setCalculatedTotalUnits(calculated);i.setTotalUnits(quantityUnknown?null:(r.totalUnits()!=null?r.totalUnits():calculated));i.setTotalUnitsOverridden(!quantityUnknown&&i.getTotalUnits()!=null&&!Objects.equals(i.getTotalUnits(),calculated));i.setQuantityUnknown(quantityUnknown);
        Set<MovementEnums.IssueType> seen=new HashSet<>();for(var issue:Optional.ofNullable(r.issues()).orElse(List.of())){if(!seen.add(issue.type()))throw BusinessException.badRequest("DUPLICATE_ISSUE","同一种异常不能重复选择。");if(issue.type()==MovementEnums.IssueType.OTHER&&(issue.description()==null||issue.description().isBlank()))throw BusinessException.badRequest("ISSUE_DESCRIPTION_REQUIRED","选择“其他”异常时必须填写说明。");MovementItemIssue entity=new MovementItemIssue();entity.setIssueType(issue.type());entity.setDescription(blank(issue.description()));i.addIssue(entity);}return i;
    }
    private void validateDirection(MovementDtos.SaveRequest r){
        if(r.returnMovement()&&r.direction()!=MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE)
            throw BusinessException.badRequest("RETURN_DIRECTION_INVALID","退回只能用于生产车间到仓库的记录。");
        if((r.direction()==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION||r.returnMovement())&&(r.manufactureLot()==null||r.manufactureLot().isBlank()))
            throw BusinessException.badRequest("MANUFACTURE_LOT_REQUIRED","仓库送往生产车间或退回物料时必须填写物料批次。");
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
