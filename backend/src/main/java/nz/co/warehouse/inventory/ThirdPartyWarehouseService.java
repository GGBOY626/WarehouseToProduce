package nz.co.warehouse.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import nz.co.warehouse.common.BusinessException;
import nz.co.warehouse.product.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class ThirdPartyWarehouseService {
    private final ThirdPartyWarehouseRepository warehouses;
    private final ThirdPartyStockRepository stocks;
    private final ProductService products;
    private final WarehouseEventRepository events;
    private final ObjectMapper json;

    public record WarehouseRequest(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=500) String location,@PositiveOrZero Long version) {}
    public record WarehouseResponse(Long id,String name,String location,long version) {}
    public record ItemRequest(Long productId,@Size(max=200) String productName,@Size(max=100) String materialCode,
                              @Size(max=100) String productionNumber,@Size(max=100) String materialBatch,@Size(max=20) String unit,
                              @Size(max=100) String batch,Boolean finished,
                              @Positive @Max(9007199254740991L) Long quantity,@PositiveOrZero Integer cartons) {}
    public record StockRequest(@NotNull Long warehouseId,@Size(max=100) String warehouseNumber,
                               @Size(max=2000) String notes,@NotEmpty @Size(max=50) List<@NotNull @Valid ItemRequest> items,@PositiveOrZero Long version) {}
    public record ItemResponse(Long productId,String productName,String materialCode,String productionNumber,String materialBatch,String unit,String batch,Boolean finished,Long quantity,Integer cartons) {}
    public record StockResponse(Long id,Long warehouseId,String warehouseName,String warehouseLocation,String warehouseNumber,
                                String notes,List<ItemResponse> items,long version,Instant createdAt,Instant updatedAt) {}
    public record Overview(List<WarehouseResponse> warehouses,List<StockResponse> stocks) {}

    @Transactional(readOnly=true)
    public Overview overview(){return new Overview(warehouses.findAllByOrderByIdAsc().stream().map(this::response).toList(),stocks.findAllByOrderByUpdatedAtDescIdDesc().stream().map(this::response).toList());}
    @Transactional
    public WarehouseResponse createWarehouse(WarehouseRequest r){
        var w=new ThirdPartyWarehouse();w.setName(r.name().trim());w.setLocation(r.location().trim());warehouses.saveAndFlush(w);
        var result=response(w);audit("EXT_WH_CREATE","新增第三方仓库 · "+w.getName(),null,result);return result;
    }
    @Transactional
    public WarehouseResponse updateWarehouse(long id,WarehouseRequest r){
        var w=warehouse(id);checkVersion(w.getVersion(),r.version());var before=response(w);
        w.setName(r.name().trim());w.setLocation(r.location().trim());warehouses.saveAndFlush(w);
        var result=response(w);audit("EXT_WH_UPDATE","更新第三方仓库 · "+w.getName(),before,result);return result;
    }
    @Transactional
    public void deleteWarehouse(long id,long version){
        var w=warehouse(id);checkVersion(w.getVersion(),version);
        if(stocks.existsByWarehouseId(id))throw conflict("WAREHOUSE_OCCUPIED","此第三方仓库仍有库存记录，请先处理库存再删除。");
        audit("EXT_WH_DELETE","删除第三方仓库 · "+w.getName(),response(w),null);warehouses.delete(w);warehouses.flush();
    }
    @Transactional
    public StockResponse createStock(StockRequest r){
        var stock=new ThirdPartyStock();apply(stock,r);stocks.saveAndFlush(stock);var result=response(stock);
        audit("EXT_STOCK_CREATE","录入第三方库存 · "+result.warehouseName()+" · 仓库号 "+result.warehouseNumber(),null,result);return result;
    }
    @Transactional
    public StockResponse updateStock(long id,StockRequest r){
        var stock=stock(id);checkVersion(stock.getVersion(),r.version());var before=response(stock);
        apply(stock,r);stock.touch();stocks.saveAndFlush(stock);var result=response(stock);
        audit("EXT_STOCK_UPDATE","更新第三方库存 · "+result.warehouseName()+" · 仓库号 "+result.warehouseNumber(),before,result);return result;
    }
    @Transactional
    public void deleteStock(long id,long version){
        var stock=stock(id);checkVersion(stock.getVersion(),version);var before=response(stock);
        audit("EXT_STOCK_DELETE","移除第三方库存 · "+before.warehouseName()+" · 仓库号 "+before.warehouseNumber(),before,null);stocks.delete(stock);stocks.flush();
    }
    private void apply(ThirdPartyStock stock,StockRequest r){
        var w=warehouse(r.warehouseId());String number=blank(r.warehouseNumber());
        if(number!=null&&stocks.existsByWarehouseIdAndWarehouseNumberIgnoreCaseAndIdNot(w.getId(),number,stock.getId()==null?-1:stock.getId()))
            throw conflict("WAREHOUSE_NUMBER_EXISTS","此第三方仓库已存在该仓库号，请编辑原记录添加物料。");
        var items=new ArrayList<ThirdPartyStock.Item>();
        for(var input:r.items()){
            var i=new ThirdPartyStock.Item();i.setStock(stock);
            if(input.productId()!=null){
                var usage=Boolean.TRUE.equals(input.finished())?ProductUsage.PRODUCTION_TO_WAREHOUSE:ProductUsage.WAREHOUSE_TO_PRODUCTION;
                var product=products.getForMovement(input.productId(),usage,true);
                i.setProduct(product);i.setProductName(product.getName());
                i.setMaterialCode(resolve(input.materialCode(),product.availableMaterialCodes(),"物料编码"));
                i.setProductionNumber(Boolean.TRUE.equals(input.finished())?null:resolve(input.productionNumber(),product.getProductionNumbers(),"生产编号"));
                i.setMaterialBatch(Boolean.TRUE.equals(input.finished())?null:resolve(input.materialBatch(),product.availableBatches(),"物料批次"));
                i.setBatch(blank(input.batch()));
                i.setUnit(blank(input.unit())==null?product.getBaseUnit():input.unit().trim());
            }else{
                if(blank(input.productName())==null)throw BusinessException.badRequest("PRODUCT_NAME_REQUIRED","请填写产品名称，或搜索并选择已有产品。");
                i.setProductName(input.productName().trim());i.setMaterialCode(blank(input.materialCode()));
                i.setProductionNumber(blank(input.productionNumber()));i.setMaterialBatch(blank(input.materialBatch()));i.setUnit(blank(input.unit()));
                i.setBatch(blank(input.batch()));
            }
            i.setFinished(input.finished());
            i.setQuantity(input.quantity());i.setCartons(input.cartons());items.add(i);
        }
        stock.setWarehouse(w);stock.setWarehouseNumber(number);stock.setNotes(blank(r.notes()));stock.getItems().clear();stock.getItems().addAll(items);
    }
    private String resolve(String input,Collection<String> choices,String label){
        if(blank(input)!=null)return input.trim();
        if(choices.size()>1)throw BusinessException.badRequest("PRODUCT_SELECTION_REQUIRED","该产品有多个"+label+"，请选择本次实际存放的"+label+"。");
        return choices.stream().findFirst().orElse(null);
    }
    private ThirdPartyWarehouse warehouse(long id){return warehouses.lockById(id).orElseThrow(()->BusinessException.notFound("THIRD_PARTY_WAREHOUSE_NOT_FOUND","第三方仓库不存在，请刷新后重试。"));}
    private ThirdPartyStock stock(long id){return stocks.findById(id).orElseThrow(()->BusinessException.notFound("THIRD_PARTY_STOCK_NOT_FOUND","第三方库存记录不存在，请刷新后重试。"));}
    private void checkVersion(long actual,Long requested){if(requested==null||actual!=requested)throw conflict("STALE_RECORD","资料已被更新，请刷新后核对再操作。");}
    private BusinessException conflict(String code,String message){return new BusinessException(code,message,HttpStatus.CONFLICT);}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private WarehouseResponse response(ThirdPartyWarehouse w){return new WarehouseResponse(w.getId(),w.getName(),w.getLocation(),w.getVersion());}
    private StockResponse response(ThirdPartyStock stock){
        var w=stock.getWarehouse();return new StockResponse(stock.getId(),w.getId(),w.getName(),w.getLocation(),stock.getWarehouseNumber(),stock.getNotes(),
            stock.getItems().stream().map(i->new ItemResponse(i.getProduct()==null?null:i.getProduct().canonicalId(),i.getProductName(),i.getMaterialCode(),i.getProductionNumber(),i.getMaterialBatch(),i.getUnit(),i.getBatch(),i.getFinished(),i.getQuantity(),i.getCartons())).toList(),
            stock.getVersion(),stock.getCreatedAt(),stock.getUpdatedAt());
    }
    private void audit(String action,String description,Object before,Object after){
        var e=new WarehouseEvent();e.setAction(action);e.setDescription(description);
        try{e.setBeforeState(before==null?null:json.writeValueAsString(before));e.setAfterState(after==null?null:json.writeValueAsString(after));}
        catch(JsonProcessingException ex){throw new IllegalStateException(ex);}events.save(e);
    }
}
