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
    public List<ProductDtos.Response> search(String q, boolean includeInactive) {
        return repository.search(q == null ? "" : q.trim(), includeInactive).stream()
                .map(p->ProductDtos.Response.from(p,repository.countByNameIgnoreCaseAndActiveTrue(p.getName())>1)).toList();
    }

    @Transactional
    public ProductDtos.Response create(ProductDtos.Request request) {
        Product p = new Product(); apply(p, request); return ProductDtos.Response.from(repository.save(p));
    }

    @Transactional
    public ProductDtos.Response update(long id, ProductDtos.Request request) {
        Product p = get(id); apply(p, request); return ProductDtos.Response.from(p);
    }

    @Transactional
    public ProductDtos.Response setActive(long id, boolean active) {
        Product p = get(id);
        if(active&&repository.existsByNameIgnoreCaseAndMaterialCodeIgnoreCaseAndActiveTrueAndIdNot(p.getName(),p.getMaterialCode(),p.getId()))
            throw new BusinessException("PRODUCT_DUPLICATE", "已有相同名称和物料编码的启用产品。", HttpStatus.CONFLICT);
        p.setActive(active); return ProductDtos.Response.from(p);
    }

    @Transactional
    public void delete(long id) {
        Product product = get(id);
        if (repository.countMovementItems(id) > 0)
            throw new BusinessException("PRODUCT_IN_USE", "该产品已经存在流转记录，不能删除，请改为停用。", HttpStatus.CONFLICT);
        repository.delete(product);
    }

    public Product getActive(long id) {
        Product p = get(id);
        if (!p.isActive()) throw new BusinessException("PRODUCT_DISABLED", "该产品已停用，请重新选择产品。", HttpStatus.CONFLICT);
        return p;
    }
    public Product getAny(long id) { return get(id); }

    private Product get(long id) { return repository.findById(id).orElseThrow(() -> BusinessException.notFound("PRODUCT_NOT_FOUND", "找不到该产品。")); }
    private void apply(Product p, ProductDtos.Request r) {
        String name=r.name().trim(), materialCode=r.materialCode().trim();
        boolean duplicate=p.getId()==null
                ?repository.existsByNameIgnoreCaseAndMaterialCodeIgnoreCaseAndActiveTrue(name,materialCode)
                :repository.existsByNameIgnoreCaseAndMaterialCodeIgnoreCaseAndActiveTrueAndIdNot(name,materialCode,p.getId());
        if(duplicate)throw new BusinessException("PRODUCT_DUPLICATE", "已有相同名称和物料编码的启用产品。", HttpStatus.CONFLICT);
        if(!r.quantityUnknown()&&(r.baseUnit()==null||r.baseUnit().isBlank()))throw BusinessException.badRequest("BASE_UNIT_REQUIRED", "数量确定的产品必须填写基础单位。");
        p.setName(name);p.setMaterialCode(materialCode);p.setMaterialBatch(blankToNull(r.materialBatch()));p.setQuantityUnknown(r.quantityUnknown());p.setDefaultUnitsPerCarton(r.quantityUnknown()?null:r.defaultUnitsPerCarton());p.setBaseUnit(r.quantityUnknown()?null:r.baseUnit().trim());
    }
    private String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
