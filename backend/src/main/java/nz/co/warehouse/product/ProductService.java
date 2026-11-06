package nz.co.warehouse.product;

import lombok.RequiredArgsConstructor;
import nz.co.warehouse.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repository;

    @Transactional(readOnly = true)
    public List<ProductDtos.Response> search(String q, boolean includeInactive, ProductUsage direction) {
        // Keep the old request flag for clients; all canonical products are now available.
        return repository.search(q == null ? "" : q.trim(), direction).stream()
                .map(p->ProductDtos.Response.from(p, repository.countConflictingNames(
                        p.getId(), p.getName(), p.getMaterialCode(), p.getMovementDirection()) > 0)).toList();
    }

    @Transactional
    public ProductDtos.Response create(ProductDtos.Request request) {
        Product p = new Product(); apply(p, request); return ProductDtos.Response.from(repository.save(p));
    }

    @Transactional
    public ProductDtos.Response update(long id, ProductDtos.Request request) {
        Product p = get(id).canonical(); apply(p, request); return ProductDtos.Response.from(p);
    }

    @Transactional
    public void delete(long id) {
        Product product = get(id);
        if (repository.countByMergedIntoId(id) > 0 || repository.countMovementItems(id) > 0 || repository.countProductionTasks(id) > 0 || repository.countWarehouseItems(id) > 0)
            throw new BusinessException("PRODUCT_IN_USE", "该产品已有流转记录、生产任务或在库托盘，不能删除。", HttpStatus.CONFLICT);
        repository.delete(product);
    }

    public Product getActive(long id) {
        return get(id).canonical();
    }
    public Product getAny(long id) { return get(id); }

    public Product getForMovement(long id, ProductUsage expectedDirection, boolean allowInactive) {
        // The legacy flag is retained for existing callers; only classification restricts selection.
        Product product = get(id).canonical();
        if (product.getMovementDirection() == null)
            throw BusinessException.badRequest("PRODUCT_DIRECTION_REQUIRED", "该产品尚未设置流转分类，请先到产品管理中补充。");
        if (product.getMovementDirection() != expectedDirection)
            throw BusinessException.badRequest("PRODUCT_DIRECTION_MISMATCH", "该产品不适用于当前流转类型，请重新选择。");
        return product;
    }

    private Product get(long id) { return repository.findById(id).orElseThrow(() -> BusinessException.notFound("PRODUCT_NOT_FOUND", "找不到该产品。")); }
    private void apply(Product p, ProductDtos.Request r) {
        String name=r.name().trim(), materialCode=r.materialCode().trim();
        boolean duplicate=p.getId()==null
                ?repository.existsByNameIgnoreCaseAndMaterialCodeIgnoreCaseAndMovementDirectionAndMergedIntoIsNull(name,materialCode,r.movementDirection())
                :repository.existsByNameIgnoreCaseAndMaterialCodeIgnoreCaseAndMovementDirectionAndMergedIntoIsNullAndIdNot(name,materialCode,r.movementDirection(),p.getId());
        if(duplicate)throw new BusinessException("PRODUCT_DUPLICATE", "已有相同名称和物料编码的产品。", HttpStatus.CONFLICT);
        if(!r.quantityUnknown()&&(r.baseUnit()==null||r.baseUnit().isBlank()))throw BusinessException.badRequest("BASE_UNIT_REQUIRED", "数量确定的产品必须填写基础单位。");
        if(r.cartonSpecs()!=null){
            if(r.cartonSpecs().size()>50||r.cartonSpecs().stream().anyMatch(n->n==null||n<=0))
                throw BusinessException.badRequest("CARTON_SPECS_INVALID", "常用箱规最多 50 项，每箱数量必须为正整数。");
            p.getCartonSpecs().clear();p.getCartonSpecs().addAll(r.cartonSpecs());
        }
        if(r.boxPackings()!=null){
            var packingKeys = new java.util.HashSet<String>();
            if(r.boxPackings().size()>50)throw BusinessException.badRequest("BOX_PACKING_INVALID","包装规格最多 50 项。");
            for(var packing:r.boxPackings()){
                if(packing==null||packing.getBoxesPerCarton()==null||packing.getUnitsPerBox()==null||packing.getBoxesPerCarton()<=0||packing.getUnitsPerBox()<=0)
                    throw BusinessException.badRequest("BOX_PACKING_INVALID","请填写有效的每箱盒数和每盒数量。");
                if(packing.getCartonsPerPallet()!=null&&packing.getCartonsPerPallet()<=0)
                    throw BusinessException.badRequest("BOX_PACKING_INVALID","每托盘箱数必须为正整数。");
                if(!packingKeys.add(packing.getBoxesPerCarton()+":"+packing.getUnitsPerBox()))
                    throw BusinessException.badRequest("BOX_PACKING_INVALID","同一箱规请只填写一次，并设置对应的托盘箱数。");
                try{packing.cartonUnits();}catch(ArithmeticException ex){throw BusinessException.badRequest("BOX_PACKING_INVALID","每箱数量超出可保存范围。");}
                if("盒".equals(r.baseUnit())&&packing.getUnitsPerBox()!=1)throw BusinessException.badRequest("BOX_PACKING_INVALID","按盒计量时，每盒基础数量应为 1；按条计量请将基础单位设为条。");
            }
            p.getBoxPackings().clear();p.getBoxPackings().addAll(r.boxPackings());
        }
        if(r.materialBatches()!=null) for(String batch:r.materialBatches()) rememberBatch(p,batch);
        rememberBatch(p,r.materialBatch());
        if(r.productionNumbers()!=null){
            if(r.productionNumbers().size()>100)throw BusinessException.badRequest("PRODUCTION_NUMBERS_INVALID","生产编号最多 100 项。");
            for(String number:r.productionNumbers())rememberProductionNumber(p,number);
        }
        var codes=new java.util.LinkedHashSet<String>();
        if(r.materialCodes()!=null)for(String code:r.materialCodes())if(code!=null&&!code.isBlank())codes.add(code.trim());
        if(!materialCode.isBlank())codes.add(materialCode);
        if(codes.isEmpty())codes.add(materialCode);
        if(codes.size()>100||codes.stream().anyMatch(code->code.length()>100))throw BusinessException.badRequest("MATERIAL_CODES_INVALID","物料编码最多 100 项，每项最多 100 个字符。");
        p.getMaterialCodes().clear();p.getMaterialCodes().addAll(codes);
        p.setName(name);p.setMaterialCode(codes.iterator().next());if(r.sku()!=null)p.setSku(blankToNull(r.sku()));p.setMaterialBatch(blankToNull(r.materialBatch()));p.setMovementDirection(r.movementDirection());p.setQuantityUnknown(r.quantityUnknown());p.setDefaultUnitsPerCarton(r.quantityUnknown()?null:r.defaultUnitsPerCarton());p.setBaseUnit(r.quantityUnknown()?null:r.baseUnit().trim());
    }
    public String resolveBatch(Product product,String value,boolean required) {
        String batch=blankToNull(value);
        if(batch!=null&&batch.length()>100)throw BusinessException.badRequest("MATERIAL_BATCH_INVALID","物料批次最多 100 个字符。");
        if(batch==null&&(required||product.availableBatches().size()>1))
            throw BusinessException.badRequest("MANUFACTURE_LOT_REQUIRED","必须填写物料批次；有多个批次时请选择本次实际使用的批次。");
        return batch;
    }
    public void rememberBatch(Product product,String value) {
        String batch=blankToNull(value);
        if(batch!=null){
            if(batch.length()>100)throw BusinessException.badRequest("MATERIAL_BATCH_INVALID","物料批次最多 100 个字符。");
            product.canonical().getMaterialBatches().add(batch);
        }
    }
    public String resolveProductionNumber(Product product,String value) {
        String number=blankToNull(value);
        if(number!=null&&number.length()>100)throw BusinessException.badRequest("PRODUCTION_NUMBER_INVALID","生产编号最多 100 个字符。");
        if(number==null&&product.canonical().getProductionNumbers().size()>1)
            throw BusinessException.badRequest("PRODUCTION_NUMBER_REQUIRED","该产品有多个生产编号，请选择本次实际使用的编号。");
        return number;
    }
    public void rememberProductionNumber(Product product,String value) {
        String number=blankToNull(value);
        if(number!=null){
            if(number.length()>100)throw BusinessException.badRequest("PRODUCTION_NUMBER_INVALID","生产编号最多 100 个字符。");
            product.canonical().getProductionNumbers().add(number);
        }
    }
    private String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
