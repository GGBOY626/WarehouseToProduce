package nz.co.warehouse.movement;

import nz.co.warehouse.person.*;
import nz.co.warehouse.product.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:warehouse;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Sql(statements="CREATE TABLE IF NOT EXISTS movement_daily_sequence (sequence_date DATE PRIMARY KEY, next_value INT NOT NULL)", executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MovementServiceIntegrationTest {
    @Autowired MovementService movements;
    @Autowired ProductService products;
    @Autowired PersonService persons;
    @Autowired JdbcTemplate jdbc;
    Long sender,receiver,product;

    @BeforeEach void setup(){
        String suffix=UUID.randomUUID().toString().substring(0,8);
        sender=persons.create(new PersonDtos.Request("仓库人员-"+suffix,null)).id();
        receiver=persons.create(new PersonDtos.Request("生产人员-"+suffix,null)).id();
        product=products.create(new ProductDtos.Request("测试产品-"+suffix,"MAT-"+suffix,"LOT-"+suffix,30,"个")).id();
    }

    @Test void createsAndReturnsSameMovementForRepeatedIdempotencyKey(){
        String key=UUID.randomUUID().toString();var first=movements.create(request(key,MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,18,20));var second=movements.create(request(key,MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,18,20));
        assertThat(second.id()).isEqualTo(first.id());assertThat(first.recordNo()).startsWith("TR-");assertThat(first.items().getFirst().totalUnits()).isEqualTo(560);assertThat(first.missingPhoto()).isTrue();
    }

    @Test void productionToWarehouseDoesNotStoreManufactureLot(){
        var saved=movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,1,0));
        assertThat(saved.manufactureLot()).isNull();
    }

    @Test void voidedMovementCannotBeEdited(){
        var saved=movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,1,0));movements.voidMovement(saved.id(),"重复登记");
        assertThatThrownBy(()->movements.update(saved.id(),request(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,2,0))).hasMessageContaining("已作废");
    }

    @Test void oneMovementSupportsSameProductWithDifferentBatchesAndManualTotal(){
        var first=new MovementDtos.ItemRequest(product,"BATCH-A",2,5,64L,"人工核对",List.of());
        var second=new MovementDtos.ItemRequest(product,"BATCH-B",3,0,null,null,List.of());
        var request=new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,Instant.parse("2026-09-28T01:00:00Z"),sender,receiver,"LOT-MULTI",null,List.of(first,second));
        var saved=movements.create(request);
        assertThat(saved.items()).hasSize(2);assertThat(saved.totalCartons()).isEqualTo(5);
        assertThat(saved.items().getFirst().totalUnits()).isEqualTo(64);assertThat(saved.items().getFirst().calculatedTotalUnits()).isEqualTo(65);assertThat(saved.items().getFirst().totalUnitsOverridden()).isTrue();
        assertThat(saved.items().get(1).batchNo()).isEqualTo("BATCH-B");
    }

    @Test void listSumsStoredTotalUnitsAcrossAllItems(){
        var first=new MovementDtos.ItemRequest(product,"BATCH-A",2,5,64L,null,List.of());
        var second=new MovementDtos.ItemRequest(product,"BATCH-B",3,0,null,null,List.of());
        var request=new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,Instant.parse("2026-09-28T01:00:00Z"),sender,receiver,"LOT-TOTAL",null,List.of(first,second));
        var saved=movements.create(request);

        var page=movements.search(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),null,null,null,null,"",0,20);

        var summary=page.content().stream().filter(x->x.id().equals(saved.id())).findFirst().orElseThrow();
        assertThat(summary.totalQuantity()).isEqualTo(154);
    }

    @Test void todayStatsMergesRepeatedProductItemsForOneDirection(){
        var first=movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,2,5));
        movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,3,0));

        var stats=movements.todayStats(LocalDate.of(2026,9,28),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION);
        var productStats=stats.products().stream().filter(x->x.productName().equals(first.items().getFirst().productName())).findFirst().orElseThrow();

        assertThat(productStats.fullCartons()).isEqualTo(5);
        assertThat(productStats.totalQuantity()).isEqualTo(155);
    }

    @Test void todayStatsCanCombineBothDirections(){
        var item=new MovementDtos.ItemRequest(product,"BATCH-BOTH",1,5,null,null,List.of());
        Instant time=Instant.parse("2026-10-15T01:00:00Z");
        movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,time,sender,receiver,"LOT-BOTH",null,List.of(item)));
        movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,time,sender,receiver,null,null,List.of(item)));

        var stats=movements.todayStats(LocalDate.of(2026,10,15),null);

        assertThat(stats.totalCartons()).isEqualTo(2);
        assertThat(stats.totalQuantity()).isEqualTo(70);
        assertThat(stats.products()).hasSize(1);
    }

    @Test void allowsAnItemWithExplicitlyUnknownQuantity(){
        var item=new MovementDtos.ItemRequest(product,"NO-LABEL",0,0,null,true,"无法清点",List.of());
        var request=new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,Instant.parse("2026-09-28T01:00:00Z"),sender,receiver,"LOT-UNKNOWN",null,List.of(item));

        var saved=movements.create(request);

        assertThat(saved.items().getFirst().quantityUnknown()).isTrue();
        assertThat(saved.items().getFirst().totalUnits()).isNull();
        assertThat(saved.totalCartons()).isZero();
        var page=movements.search(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),null,null,null,null,saved.recordNo(),0,20);
        assertThat(page.content().getFirst().hasUnknownQuantity()).isTrue();
    }

    @Test void productConfiguredWithUnknownQuantityNeedsNoUnitAndMakesMovementQuantityUnknown(){
        String suffix=UUID.randomUUID().toString().substring(0,8);
        var unknownProduct=products.create(new ProductDtos.Request("无法清点产品-"+suffix,"UNKNOWN-"+suffix,null,null,null,true));
        var item=new MovementDtos.ItemRequest(unknownProduct.id(),"UNKNOWN-"+suffix,0,0,null,false,null,List.of());
        var request=new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,Instant.parse("2026-09-28T01:00:00Z"),sender,receiver,"LOT-UNKNOWN-PRODUCT",null,List.of(item));

        var saved=movements.create(request);

        assertThat(unknownProduct.quantityUnknown()).isTrue();
        assertThat(unknownProduct.baseUnit()).isNull();
        assertThat(saved.items().getFirst().quantityUnknown()).isTrue();
        assertThat(saved.items().getFirst().totalUnits()).isNull();
    }

    @Test void queryStatsUsesTheSameDirectionAndMissingPhotoFiltersAsHistorySearch(){
        var outbound=movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,2,5));
        var inbound=movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,3,0));
        jdbc.update("""
                insert into movement_photo
                    (movement_id,file_path,original_name,mime_type,file_size,width,height,created_at,updated_at)
                values (?, 'test/inbound.jpg', 'inbound.jpg', 'image/jpeg', 100, 100, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,inbound.id());

        var stats=movements.queryStats(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,MovementEnums.Status.ACTIVE,true,true,outbound.items().getFirst().productName());

        assertThat(stats.directions()).hasSize(1);
        assertThat(stats.directions().getFirst().direction()).isEqualTo(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION);
        assertThat(stats.directions().getFirst().totalCartons()).isEqualTo(2);
        assertThat(stats.directions().getFirst().totalQuantity()).isEqualTo(65);
        assertThat(stats.directions().getFirst().products()).extracting(MovementDtos.ProductStatResponse::productName).containsExactly(outbound.items().getFirst().productName());
    }

    @Test void detailDoesNotDuplicateItemWhenMovementHasMultiplePhotos(){
        var saved=movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,1,0));
        for(int i=1;i<=2;i++)jdbc.update("""
                insert into movement_photo
                    (movement_id,file_path,original_name,mime_type,file_size,width,height,created_at,updated_at)
                values (?, ?, ?, 'image/jpeg', 100, 100, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,saved.id(),"test/photo-"+i+".jpg","photo-"+i+".jpg");

        var detail=movements.detail(saved.id());

        assertThat(detail.photos()).hasSize(2);
        assertThat(detail.items()).hasSize(1);
        assertThat(detail.items().getFirst().id()).isEqualTo(saved.items().getFirst().id());
    }

    @Test void deletingMovementRemovesItsItemsAndReleasesProduct(){
        var saved=movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,1,0));
        assertThatThrownBy(()->products.delete(product)).hasMessageContaining("不能删除");

        movements.delete(saved.id());

        assertThat(jdbc.queryForObject("select count(*) from movement where id=?",Long.class,saved.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from movement_item where movement_id=?",Long.class,saved.id())).isZero();
        assertThatCode(()->products.delete(product)).doesNotThrowAnyException();
    }

    private MovementDtos.SaveRequest request(String key,MovementEnums.Direction direction,int cartons,long loose){
        var item=new MovementDtos.ItemRequest(product,"BATCH-001",cartons,loose,null,"测试",List.of(new MovementDtos.IssueRequest(MovementEnums.IssueType.MISSING_LABEL,"少贴标签")));
        return new MovementDtos.SaveRequest(key,direction,Instant.parse("2026-09-28T00:00:00Z"),sender,receiver,direction==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION?"CA202607006":null,"整单备注",List.of(item));
    }
}
