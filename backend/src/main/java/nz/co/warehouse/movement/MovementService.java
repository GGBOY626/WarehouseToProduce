package nz.co.warehouse.movement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.co.warehouse.common.BusinessException;
import nz.co.warehouse.person.*;
import nz.co.warehouse.product.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

@Slf4j @Service @RequiredArgsConstructor
public class MovementService {
    private final MovementRepository repository;
    private final ProductService products;
    private final PersonService persons;
    private final RecordNumberService numbers;
    private final TransactionTemplate transactions;
    @Value("${app.business-zone:Pacific/Auckland}") private String zone;

    public MovementDtos.DetailResponse create(MovementDtos.SaveRequest request) {
        var existing=repository.findByIdempotencyKey(request.idempotencyKey());
        if(existing.isPresent()){log.info("幂等重复请求: {}",request.idempotencyKey());return detail(existing.get().getId());}
        try {
            return transactions.execute(status->{
                Movement m=new Movement();
                m.setIdempotencyKey(request.idempotencyKey());
                m.setRecordNo(numbers.next(request.movementTime()));
                apply(m,request);
                return MovementDtos.detail(repository.saveAndFlush(m));
            });
        } catch(DataIntegrityViolationException ex) {
            return repository.findByIdempotencyKey(request.idempotencyKey()).map(x->detail(x.getId())).orElseThrow(()->ex);
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

    @Transactional(readOnly=true)
    public MovementDtos.DetailResponse detail(long id){return MovementDtos.detail(repository.findDetailById(id).orElseThrow(()->BusinessException.notFound("MOVEMENT_NOT_FOUND","找不到该流转记录。")));}

    @Transactional(readOnly=true)
    public MovementDtos.PageResponse search(LocalDate from,LocalDate to,MovementEnums.Direction direction,MovementEnums.Status status,Boolean missingPhoto,Boolean hasIssue,String q,int page,int size){
        ZoneId z=ZoneId.of(zone);Instant start=from.atStartOfDay(z).toInstant();Instant end=to.plusDays(1).atStartOfDay(z).toInstant();
        Page<Movement> result=repository.search(start,end,direction,status,missingPhoto,hasIssue,q==null?"":q.trim(),PageRequest.of(page,Math.min(size,100),Sort.by(Sort.Direction.DESC,"movementTime")));
        return new MovementDtos.PageResponse(result.getContent().stream().map(MovementDtos::summary).toList(),page,result.getSize(),result.getTotalElements(),result.getTotalPages());
    }

    private void apply(Movement m,MovementDtos.SaveRequest r){
        validateDirection(r);
        Person sender=m.getId()!=null&&m.getSenderPerson().getId().equals(r.senderPersonId())?persons.getAny(r.senderPersonId()):persons.getActive(r.senderPersonId());
        Person receiver=m.getId()!=null&&m.getReceiverPerson().getId().equals(r.receiverPersonId())?persons.getAny(r.receiverPersonId()):persons.getActive(r.receiverPersonId());
        m.setDirection(r.direction());m.setMovementTime(r.movementTime());m.setSenderPerson(sender);m.setSenderNameSnapshot(sender.getName());m.setReceiverPerson(receiver);m.setReceiverNameSnapshot(receiver.getName());
        m.setManufactureLot(r.direction()==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION?r.manufactureLot().trim():null);m.setRemarks(blank(r.remarks()));
        Set<Long> existingProductIds=m.getItems().stream().map(x->x.getProduct().getId()).collect(java.util.stream.Collectors.toSet());
        m.getItems().clear();int cartons=0;int order=0;
        for(var input:r.items()){MovementItem item=buildItem(input,order++,existingProductIds.contains(input.productId()));m.addItem(item);cartons+=input.fullCartons();}
        m.setTotalCartons(cartons);
    }

    private MovementItem buildItem(MovementDtos.ItemRequest r,int order,boolean allowInactive){
        if(!QuantityRules.hasQuantity(r.fullCartons(),r.looseUnits(),r.totalUnits()))throw BusinessException.badRequest("QUANTITY_REQUIRED","每个产品至少填写一种数量。");
        Product p=allowInactive?products.getAny(r.productId()):products.getActive(r.productId());MovementItem i=new MovementItem();i.setProduct(p);i.setProductNameSnapshot(p.getName());i.setSkuSnapshot(p.getSku());i.setUnitsPerCartonSnapshot(p.getDefaultUnitsPerCarton());i.setBaseUnitSnapshot(p.getBaseUnit());i.setBatchNo(r.batchNo().trim());i.setFullCartons(r.fullCartons());i.setLooseUnits(r.looseUnits());i.setSortOrder(order);i.setRemarks(blank(r.remarks()));
        Long calculated=QuantityRules.calculate(p.getDefaultUnitsPerCarton(),r.fullCartons(),r.looseUnits());
        i.setCalculatedTotalUnits(calculated);i.setTotalUnits(r.totalUnits()!=null?r.totalUnits():calculated);i.setTotalUnitsOverridden(i.getTotalUnits()!=null&&!Objects.equals(i.getTotalUnits(),calculated));
        Set<MovementEnums.IssueType> seen=new HashSet<>();for(var issue:Optional.ofNullable(r.issues()).orElse(List.of())){if(!seen.add(issue.type()))throw BusinessException.badRequest("DUPLICATE_ISSUE","同一种异常不能重复选择。");if(issue.type()==MovementEnums.IssueType.OTHER&&(issue.description()==null||issue.description().isBlank()))throw BusinessException.badRequest("ISSUE_DESCRIPTION_REQUIRED","选择“其他”异常时必须填写说明。");MovementItemIssue entity=new MovementItemIssue();entity.setIssueType(issue.type());entity.setDescription(blank(issue.description()));i.addIssue(entity);}return i;
    }
    private void validateDirection(MovementDtos.SaveRequest r){if(r.direction()==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION&&(r.manufactureLot()==null||r.manufactureLot().isBlank()))throw BusinessException.badRequest("MANUFACTURE_LOT_REQUIRED","仓库送往生产车间时必须填写 Manufacture Lot。");}
    private Movement getActiveDetail(long id){Movement m=repository.findDetailById(id).orElseThrow(()->BusinessException.notFound("MOVEMENT_NOT_FOUND","找不到该流转记录。"));if(m.getStatus()==MovementEnums.Status.VOID)throw new BusinessException("MOVEMENT_VOID","已作废记录不能修改。",HttpStatus.CONFLICT);return m;}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
}
