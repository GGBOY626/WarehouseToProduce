package nz.co.warehouse.movement;

import nz.co.warehouse.product.ProductService;
import nz.co.warehouse.product.ProductDtos;
import nz.co.warehouse.product.ProductUsage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:sku-receipt;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"
})
@Sql(statements="CREATE TABLE IF NOT EXISTS movement_daily_sequence (sequence_date DATE PRIMARY KEY, next_value INT NOT NULL)")
class FinishedReceiptSkuIntegrationTest {
    @Autowired ProductService products;
    @Autowired MovementService movements;

    private ProductDtos.Response product(String sku) {
        String suffix=UUID.randomUUID().toString();
        return products.create(new ProductDtos.Request("SKU product "+suffix,"CODE-"+suffix,sku,null,
                ProductUsage.PRODUCTION_TO_WAREHOUSE,null,"unit",false,List.of(),List.of(),List.of(),List.of()));
    }

    private MovementDtos.ItemRequest item(ProductDtos.Response product,Long id,String sku) {
        return new MovementDtos.ItemRequest(product.id(),product.materialCode(),0,1L,null,false,null,List.of(),
                null,0,null,id,null,"",null,null,null,null,sku);
    }

    private MovementDtos.SaveRequest request(MovementDtos.ItemRequest item) {
        return new MovementDtos.SaveRequest(UUID.randomUUID().toString(),
                MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,Instant.parse("2026-11-01T00:00:00Z"),
                null,null,null,false,null,null,null,null,List.of(item));
    }

    @Test void defaultsToProductSkuAndKeepsSnapshotAfterCatalogChanges() {
        var product=product("CATALOG-SKU");
        var saved=movements.create(request(item(product,null,null)));
        assertThat(saved.items().getFirst().sku()).isEqualTo("CATALOG-SKU");
        assertThat(products.search("CATALOG-SKU",true,ProductUsage.PRODUCTION_TO_WAREHOUSE))
                .extracting(ProductDtos.Response::id).contains(product.id());
        products.update(product.id(),new ProductDtos.Request(product.name(),product.materialCode(),"NEW-SKU",null,
                ProductUsage.PRODUCTION_TO_WAREHOUSE,null,"unit",false,List.of(),List.of(),List.of(),List.of()));
        assertThat(movements.detail(saved.id()).items().getFirst().sku()).isEqualTo("CATALOG-SKU");
        var edited=movements.update(saved.id(),request(item(product,saved.items().getFirst().id(),null)));
        assertThat(edited.items().getFirst().sku()).isEqualTo("CATALOG-SKU");
    }

    @Test void acceptsOverridesAndExplicitClearingOnReceipts() {
        var product=product("DEFAULT-SKU");
        var saved=movements.create(request(item(product,null,"  ACTUAL-SKU  ")));
        assertThat(saved.items().getFirst().sku()).isEqualTo("ACTUAL-SKU");
        var cleared=movements.update(saved.id(),request(item(product,saved.items().getFirst().id(),"")));
        assertThat(cleared.items().getFirst().sku()).isNull();
        assertThat(movements.detail(saved.id()).items().getFirst().sku()).isNull();
        assertThat(movements.create(request(item(product,null,""))).items().getFirst().sku()).isNull();
    }

    @Test void savesAndReturnsSkuOnFinishedReceipt() {
        String suffix=UUID.randomUUID().toString();
        var product=products.create(new ProductDtos.Request("SKU product "+suffix,"CODE-"+suffix,"SKU-"+suffix,null,
                ProductUsage.PRODUCTION_TO_WAREHOUSE,null,"个",false,List.of(),List.of(),List.of(),List.of()));
        var item=new MovementDtos.ItemRequest(product.id(),product.materialCode(),0,1L,null,false,null,List.of(),
                null,0,null,null,null,null,null,null,null,null,product.sku());
        var saved=movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),
                MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,Instant.parse("2026-11-01T00:00:00Z"),
                null,null,null,false,null,null,null,null,List.of(item)));

        assertThat(saved.items()).singleElement().extracting(MovementDtos.ItemResponse::sku).isEqualTo(product.sku());
        assertThat(movements.detail(saved.id()).items()).singleElement()
                .extracting(MovementDtos.ItemResponse::sku).isEqualTo(product.sku());
    }
}
