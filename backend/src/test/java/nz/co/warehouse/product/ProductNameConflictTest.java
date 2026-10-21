package nz.co.warehouse.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(ProductService.class)
class ProductNameConflictTest {
    @Autowired ProductService products;
    @Autowired ProductRepository repository;

    private Product add(String name, String code, ProductUsage usage) {
        Product p = new Product();
        p.setName(name); p.setMaterialCode(code); p.setMovementDirection(usage);
        return repository.saveAndFlush(p);
    }

    @Test void sameNameAcrossActualProductClassificationsDoesNotConflict() {
        var material = add("关节饮", "RAW-1", ProductUsage.WAREHOUSE_TO_PRODUCTION);
        var finished = add("关节饮", "FIN-1", ProductUsage.PRODUCTION_TO_WAREHOUSE);
        assertThat(products.search("关节", false, null)).hasSize(2).allMatch(p -> !p.duplicateName());
        for (var usage : ProductUsage.values())
            assertThat(products.search("", false, usage)).singleElement().satisfies(p -> assertThat(p.duplicateName()).isFalse());
        // Returning material uses the material classification, even though its physical direction is inbound.
        assertThat(products.getForMovement(material.getId(), ProductUsage.WAREHOUSE_TO_PRODUCTION, false)).isSameAs(material);
        assertThatThrownBy(() -> products.getForMovement(finished.getId(), ProductUsage.WAREHOUSE_TO_PRODUCTION, false))
                .hasMessageContaining("不适用于当前流转类型");
    }

    @Test void sameTypeDifferentCodesWarnEvenWhenSearchOnlyMatchesOneCode() {
        add("Joint Drink", "RAW-1", ProductUsage.WAREHOUSE_TO_PRODUCTION);
        add("joint drink", "RAW-2", ProductUsage.WAREHOUSE_TO_PRODUCTION);
        add("Joint Drink", "FIN-1", ProductUsage.PRODUCTION_TO_WAREHOUSE);
        assertThat(products.search("RAW-1", false, ProductUsage.WAREHOUSE_TO_PRODUCTION))
                .singleElement().satisfies(p -> assertThat(p.duplicateName()).isTrue());
        assertThat(products.search("", false, ProductUsage.WAREHOUSE_TO_PRODUCTION)).allMatch(ProductDtos.Response::duplicateName);
        assertThat(products.search("", false, ProductUsage.PRODUCTION_TO_WAREHOUSE)).allMatch(p -> !p.duplicateName());
    }

    @Test void inactiveMergedAndSameCodeRowsAreNotDifferentCodeConflicts() {
        var original = add("原料", "RAW", ProductUsage.WAREHOUSE_TO_PRODUCTION);
        var inactive = add("原料", "OLD", ProductUsage.WAREHOUSE_TO_PRODUCTION);
        inactive.setActive(false);
        var merged = add("原料", "ALIAS", ProductUsage.WAREHOUSE_TO_PRODUCTION);
        merged.setMergedInto(original);
        add("原料", "raw", ProductUsage.WAREHOUSE_TO_PRODUCTION);
        assertThat(products.search("", false, null)).hasSize(2).allMatch(p -> !p.duplicateName());
    }

    @Test void finishedGoodsStillWarnForDifferentCodesWithinTheirOwnClassification() {
        add("成品", "FIN-1", ProductUsage.PRODUCTION_TO_WAREHOUSE);
        add("成品", "FIN-2", ProductUsage.PRODUCTION_TO_WAREHOUSE);
        assertThat(products.search("FIN-1", false, ProductUsage.PRODUCTION_TO_WAREHOUSE))
                .singleElement().satisfies(p -> assertThat(p.duplicateName()).isTrue());
        // Existing records can also share both name and code across classifications.
        add("同名同码", "SHARED", ProductUsage.PRODUCTION_TO_WAREHOUSE);
        add("同名同码", "SHARED", ProductUsage.WAREHOUSE_TO_PRODUCTION);
        assertThat(products.search("SHARED", false, null)).hasSize(2).allMatch(p -> !p.duplicateName());
    }

    @Test void unclassifiedProductsAreNotAssumedToBeMaterialsOrFinishedGoods() {
        add("旧产品", "UNKNOWN-1", null);
        add("旧产品", "RAW", ProductUsage.WAREHOUSE_TO_PRODUCTION);
        assertThat(products.search("", false, null)).allMatch(p -> !p.duplicateName());
        add("旧产品", "UNKNOWN-2", null);
        assertThat(products.search("UNKNOWN", false, null)).hasSize(2).allMatch(ProductDtos.Response::duplicateName);
        assertThat(products.search("RAW", false, null)).singleElement().satisfies(p -> assertThat(p.duplicateName()).isFalse());
    }
}
