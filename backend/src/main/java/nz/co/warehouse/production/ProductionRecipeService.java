package nz.co.warehouse.production;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import nz.co.warehouse.common.BusinessException;
import nz.co.warehouse.product.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service @RequiredArgsConstructor
public class ProductionRecipeService {
    private final ProductionRecipeRepository recipes;
    private final ProductService products;

    public record ItemRequest(@NotNull Long productId, @NotNull @DecimalMin("0.000001") BigDecimal quantityPerOutput) {}
    public record SaveRequest(@jakarta.validation.constraints.NotBlank @Size(max = 100) String name,
                              @NotNull Long primaryMaterialProductId,
                              @NotEmpty List<@Valid ItemRequest> items) {}
    public record ItemResponse(Long productId, String productName, String materialCode, String baseUnit,
                               ProductMaterialCategory materialCategory, BigDecimal quantityPerOutput, boolean primary) {}
    public record Response(Long id, Long outputProductId, String outputProductName, String name,
                           Long primaryMaterialProductId, List<ItemResponse> items) {}

    @Transactional(readOnly = true)
    public List<Response> list(long outputProductId) {
        Product output = products.getForMovement(outputProductId, ProductUsage.PRODUCTION_TO_WAREHOUSE, true);
        if (output.isQuantityUnknown()) throw BusinessException.badRequest("RECIPE_OUTPUT_INVALID", "成品必须有确定的计划数量口径。");
        return recipes.findAllByOutputProductIdOrderByName(output.canonicalId()).stream().map(this::response).toList();
    }

    @Transactional
    public Response create(long outputProductId, SaveRequest request) {
        Product output = output(outputProductId);
        String name = request.name().trim();
        if (recipes.existsByOutputProductIdAndNameIgnoreCase(output.getId(), name))
            throw new BusinessException("RECIPE_DUPLICATE", "这个成品已有同名配方。", HttpStatus.CONFLICT);
        ProductionRecipe recipe = new ProductionRecipe();
        recipe.setOutputProduct(output);
        apply(recipe, name, request);
        return response(recipes.saveAndFlush(recipe));
    }

    @Transactional
    public Response update(long id, SaveRequest request) {
        ProductionRecipe recipe = get(id);
        String name = request.name().trim();
        if (recipes.existsByOutputProductIdAndNameIgnoreCaseAndIdNot(recipe.getOutputProduct().getId(), name, id))
            throw new BusinessException("RECIPE_DUPLICATE", "这个成品已有同名配方。", HttpStatus.CONFLICT);
        apply(recipe, name, request);
        return response(recipe);
    }

    @Transactional
    public void delete(long id) {
        ProductionRecipe recipe = get(id);
        if (recipes.countTasksByRecipeId(id) > 0)
            throw BusinessException.badRequest("RECIPE_IN_USE", "已有任务使用这个配方，请先保留配方并创建新配方名称。");
        recipes.delete(recipe);
    }

    @Transactional(readOnly = true)
    public ProductionRecipe getAny(long id) { return get(id); }

    private Product output(long id) {
        Product output = products.getForMovement(id, ProductUsage.PRODUCTION_TO_WAREHOUSE, false);
        if (output.isQuantityUnknown()) throw BusinessException.badRequest("RECIPE_OUTPUT_INVALID", "成品必须有确定的计划数量口径。");
        return output;
    }

    private void apply(ProductionRecipe recipe, String name, SaveRequest request) {
        Product primary = products.getForMovement(request.primaryMaterialProductId(), ProductUsage.WAREHOUSE_TO_PRODUCTION, false);
        if (request.items().size() > 50) throw BusinessException.badRequest("RECIPE_ITEMS_LIMIT", "一个配方最多添加 50 种物料。");
        Set<Long> ids = new HashSet<>();
        List<ProductionRecipeItem> updated = new ArrayList<>();
        Map<Long, ProductionRecipeItem> existingItems = new HashMap<>();
        recipe.getItems().forEach(item -> existingItems.put(item.getProduct().canonicalId(), item));
        boolean hasPrimary = false;
        for (ItemRequest input : request.items()) {
            if (input == null || input.productId() == null || input.quantityPerOutput() == null || input.quantityPerOutput().signum() <= 0)
                throw BusinessException.badRequest("RECIPE_ITEM_INVALID", "每种物料都要选择产品并填写大于零的每个成品数用量。");
            BigDecimal quantity;
            try { quantity = input.quantityPerOutput().setScale(6, RoundingMode.UNNECESSARY); }
            catch (ArithmeticException ex) { throw BusinessException.badRequest("RECIPE_ITEM_INVALID", "物料用量最多支持 6 位小数。"); }
            if (quantity.precision() > 18) throw BusinessException.badRequest("RECIPE_ITEM_INVALID", "物料用量超出可保存范围。");
            Product product = products.getForMovement(input.productId(), ProductUsage.WAREHOUSE_TO_PRODUCTION, false);
            if (!ids.add(product.canonicalId())) throw BusinessException.badRequest("RECIPE_ITEM_DUPLICATE", "同一种物料在配方中只填写一行。");
            if (product.canonicalId().equals(primary.canonicalId())) hasPrimary = true;
            ProductionRecipeItem item = existingItems.getOrDefault(product.canonicalId(), new ProductionRecipeItem());
            item.setRecipe(recipe); item.setProduct(product); item.setQuantityPerOutput(quantity); item.setSortOrder(updated.size());
            updated.add(item);
        }
        if (!hasPrimary) throw BusinessException.badRequest("RECIPE_PRIMARY_REQUIRED", "主料必须包含在配方物料中。");
        recipe.setName(name); recipe.setPrimaryMaterial(primary); recipe.getItems().clear(); recipe.getItems().addAll(updated);
    }

    private ProductionRecipe get(long id) {
        return recipes.findById(id).orElseThrow(() -> BusinessException.notFound("RECIPE_NOT_FOUND", "找不到这个生产配方。"));
    }

    private Response response(ProductionRecipe recipe) {
        return new Response(recipe.getId(), recipe.getOutputProduct().canonicalId(), recipe.getOutputProduct().getName(), recipe.getName(),
                recipe.getPrimaryMaterial().canonicalId(), recipe.getItems().stream().map(item -> {
                    Product product = item.getProduct().canonical();
                    return new ItemResponse(product.getId(), product.getName(), product.getMaterialCode(), product.getBaseUnit(),
                            product.getMaterialCategory(), item.getQuantityPerOutput(), product.getId().equals(recipe.getPrimaryMaterial().canonicalId()));
                }).toList());
    }
}
