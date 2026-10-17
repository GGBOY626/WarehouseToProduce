package nz.co.warehouse.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import nz.co.warehouse.common.BusinessException;
import nz.co.warehouse.product.ProductService;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class WarehouseService {
    private final WarehouseAreaRepository areas;
    private final WarehousePalletRepository pallets;
    private final WarehouseEventRepository events;
    private final ProductService products;
    private final ObjectMapper json;
    public record AreaRequest(@NotBlank @Size(max=60) String name,@Min(2) @Max(4) int lanes,@PositiveOrZero Long version) {}
    public record AreaResponse(Long id,String name,WarehouseArea.Kind kind,int lanes,long version) {}
    public record ItemRequest(@NotNull Long productId,@Size(max=100) String batch,@Positive @Max(9007199254740991L) Long quantity,@PositiveOrZero Integer cartons) {}
    public record SaveRequest(@NotBlank @Size(max=60) String code,@NotNull Long areaId,@NotBlank String lane,
                              @Min(1) @Max(9999) int position,@Size(max=2000) String notes,
                              @NotEmpty @Size(max=50) List<@Valid ItemRequest> items,@PositiveOrZero Long version) {}
    public record ItemResponse(Long productId,String productName,String materialCode,String batch,String unit,Long quantity,Integer cartons) {}
    public record PalletResponse(Long id,String code,Long areaId,String areaName,WarehouseArea.Kind kind,String lane,int position,
                                 String location,String notes,List<ItemResponse> items,long version,Instant createdAt,Instant updatedAt) {}
    public record Overview(List<AreaResponse> areas,List<PalletResponse> pallets) {}
    public record History(List<WarehouseEvent> content,int totalPages,long totalElements) {}

    @Transactional(readOnly=true)
    public Overview overview(){return new Overview(areas.findAllByOrderByIdAsc().stream().map(this::areaResponse).toList(),pallets.findAllByOrderByUpdatedAtDescIdDesc().stream().map(this::response).toList());}
    @Transactional(readOnly=true)
    public History history(Long palletId,int page){
        if(page<0)throw BusinessException.badRequest("PAGE_INVALID","页码不能为负数。");
        var pageable=PageRequest.of(page,30,Sort.by(Sort.Direction.DESC,"id"));
        var result=palletId==null?events.findAll(pageable):events.findByPalletId(palletId,pageable);
        return new History(result.getContent(),result.getTotalPages(),result.getTotalElements());
    }
    @Transactional
    public AreaResponse createAisle(AreaRequest r){
        checkLanes(r.lanes());var a=new WarehouseArea();a.setKind(WarehouseArea.Kind.AISLE);a.setName(r.name().trim());a.setLanes(r.lanes());
        areas.saveAndFlush(a);audit(null,"AREA_CREATE","新增过道 "+a.getName()+"（"+a.getLanes()+" 排）",null,null);return areaResponse(a);
    }
    @Transactional
    public AreaResponse updateAisle(long id,AreaRequest r){
        var a=area(id);checkAisle(a);checkVersion(a.getVersion(),r.version());checkLanes(r.lanes());
        if(r.lanes()==2&&pallets.existsByAreaIdAndLaneIn(id,List.of("LEFT_2","RIGHT_2")))throw conflict("AISLE_OCCUPIED","左二或右二仍有托盘，请先移走再改为两排。");
        String old=a.getName()+"（"+a.getLanes()+" 排）";a.setName(r.name().trim());a.setLanes(r.lanes());areas.saveAndFlush(a);
        audit(null,"AREA_UPDATE",old+" → "+a.getName()+"（"+a.getLanes()+" 排）",null,null);return areaResponse(a);
    }
    @Transactional
    public void deleteAisle(long id,long version){
        var a=area(id);checkAisle(a);checkVersion(a.getVersion(),version);
        if(pallets.existsByAreaId(id))throw conflict("AISLE_OCCUPIED","过道仍有托盘，请先移走再删除。");
        audit(null,"AREA_DELETE","删除过道 "+a.getName(),null,null);areas.delete(a);
    }
    @Transactional
    public PalletResponse create(SaveRequest r){
        var p=new WarehousePallet();apply(p,r);pallets.saveAndFlush(p);var result=response(p);audit(p,"CREATE","录入托盘 · "+result.location(),null,result);return result;
    }
    @Transactional
    public PalletResponse update(long id,SaveRequest r){
        var p=pallet(id);checkVersion(p.getVersion(),r.version());var before=response(p);apply(p,r);
        // A stock-only edit must also advance the parent version.
        p.setUpdatedAtForInventory(Instant.now());
        pallets.saveAndFlush(p);var after=response(p);audit(p,"UPDATE",before.location().equals(after.location())?"更新托盘 · "+after.location():"移位 · "+before.location()+" → "+after.location(),before,after);return after;
    }
    @Transactional
    public void delete(long id,long version){
        var p=pallet(id);checkVersion(p.getVersion(),version);var before=response(p);
        audit(p,"DELETE","移除托盘 · "+before.location(),before,null);pallets.delete(p);pallets.flush();
    }
    private void apply(WarehousePallet p,SaveRequest r){
        var a=area(r.areaId());
        var allowed=a.getKind()==WarehouseArea.Kind.RACK?List.of("UPPER","MIDDLE","LOWER"):a.getLanes()==2?List.of("LEFT_1","RIGHT_1"):List.of("LEFT_1","LEFT_2","RIGHT_1","RIGHT_2");
        if(!allowed.contains(r.lane()))throw BusinessException.badRequest("LOCATION_INVALID","层位或过道排位无效，请重新选择。");
        if(pallets.existsByAreaIdAndLaneAndPositionAndIdNot(a.getId(),r.lane(),r.position(),p.getId()==null?-1:p.getId()))throw conflict("LOCATION_OCCUPIED","这个位置已有托盘，请选择其他位置，或编辑原托盘添加物料。");
        var existing=new HashMap<Long,WarehousePallet.StockItem>();p.getItems().forEach(i->existing.put(i.getProduct().canonicalId(),i));
        var items=new ArrayList<WarehousePallet.StockItem>();
        for(var input:r.items()){
            var product=products.getAny(input.productId()).canonical();var old=existing.get(product.getId());
            if(old==null&&!product.isActive())throw BusinessException.badRequest("PRODUCT_DISABLED","新增物料不能选择已停用产品。");
            var i=new WarehousePallet.StockItem();i.setPallet(p);i.setProduct(product);i.setProductName(old==null?product.getName():old.getProductName());i.setMaterialCode(old==null?product.getMaterialCode():old.getMaterialCode());
            i.setUnit(old==null?product.getBaseUnit():old.getUnit());
            if(input.quantity()!=null&&(i.getUnit()==null||i.getUnit().isBlank()))throw BusinessException.badRequest("UNIT_REQUIRED","该产品没有数量单位，请先完善产品资料，或留空数量稍后盘点。");
            i.setBatch(blank(input.batch()));i.setQuantity(input.quantity());i.setCartons(input.cartons());items.add(i);
        }
        p.setCode(r.code().trim());p.setArea(a);p.setLane(r.lane());p.setPosition(r.position());p.setNotes(blank(r.notes()));p.getItems().clear();p.getItems().addAll(items);
    }
    private WarehouseArea area(long id){return areas.lockById(id).orElseThrow(()->BusinessException.notFound("AREA_NOT_FOUND","找不到库区，请刷新后重试。"));}
    private WarehousePallet pallet(long id){return pallets.findById(id).orElseThrow(()->BusinessException.notFound("PALLET_NOT_FOUND","找不到托盘，请刷新后重试。"));}
    private void checkAisle(WarehouseArea a){if(a.getKind()!=WarehouseArea.Kind.AISLE)throw BusinessException.badRequest("RACK_FIXED","A–M 货架为固定库区，无需修改或删除。");}
    private void checkLanes(int lanes){if(lanes!=2&&lanes!=4)throw BusinessException.badRequest("LANES_INVALID","过道只支持两排或四排。");}
    private void checkVersion(long actual,Long requested){if(requested==null||actual!=requested)throw conflict("STALE_RECORD","资料已被更新，请刷新后重新操作，避免覆盖其他人的修改。");}
    private BusinessException conflict(String code,String message){return new BusinessException(code,message,HttpStatus.CONFLICT);}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private AreaResponse areaResponse(WarehouseArea a){return new AreaResponse(a.getId(),a.getName(),a.getKind(),a.getLanes(),a.getVersion());}
    private PalletResponse response(WarehousePallet p){
        var a=p.getArea();String lane=switch(p.getLane()){case "UPPER"->"上层";case "MIDDLE"->"中层";case "LOWER"->"下层";case "LEFT_1"->"左一";case "LEFT_2"->"左二";case "RIGHT_1"->"右一";default->"右二";};
        return new PalletResponse(p.getId(),p.getCode(),a.getId(),a.getName(),a.getKind(),p.getLane(),p.getPosition(),a.getName()+(a.getKind()==WarehouseArea.Kind.RACK?" 排":"")+" / "+lane+" / "+String.format("%02d",p.getPosition())+" 号",p.getNotes(),p.getItems().stream().map(i->new ItemResponse(i.getProduct().canonicalId(),i.getProductName(),i.getMaterialCode(),i.getBatch(),i.getUnit(),i.getQuantity(),i.getCartons())).toList(),p.getVersion(),p.getCreatedAt(),p.getUpdatedAt());
    }
    private void audit(WarehousePallet p,String action,String description,PalletResponse before,PalletResponse after){
        var e=new WarehouseEvent();if(p!=null){e.setPalletId(p.getId());e.setPalletCode(p.getCode());}e.setAction(action);e.setDescription(description);
        try{e.setBeforeState(before==null?null:json.writeValueAsString(before));e.setAfterState(after==null?null:json.writeValueAsString(after));}catch(JsonProcessingException ex){throw new IllegalStateException(ex);}
        events.save(e);
    }
}
