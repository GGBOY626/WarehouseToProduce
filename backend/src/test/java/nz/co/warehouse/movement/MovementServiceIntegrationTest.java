package nz.co.warehouse.movement;

import nz.co.warehouse.person.*;
import nz.co.warehouse.product.*;
import nz.co.warehouse.production.*;
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
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Sql(statements="CREATE TABLE IF NOT EXISTS movement_daily_sequence (sequence_date DATE PRIMARY KEY, next_value INT NOT NULL)", executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MovementServiceIntegrationTest {
    @Autowired MovementService movements;
    @Autowired ProductService products;
    @Autowired PersonService persons;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductionTaskService productionTasks;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    Long sender,receiver,product,inboundProduct;

    @BeforeEach void setup(){
        String suffix=UUID.randomUUID().toString().substring(0,8);
        sender=persons.create(new PersonDtos.Request("仓库人员-"+suffix,null)).id();
        receiver=persons.create(new PersonDtos.Request("生产人员-"+suffix,null)).id();
        product=products.create(new ProductDtos.Request("测试产品-"+suffix,"MAT-"+suffix,"LOT-"+suffix,ProductUsage.WAREHOUSE_TO_PRODUCTION,30,"个")).id();
        inboundProduct=products.create(new ProductDtos.Request("入库产品-"+suffix,"IN-"+suffix,null,ProductUsage.PRODUCTION_TO_WAREHOUSE,30,"个")).id();
    }

    @Test void createsAndReturnsSameMovementForRepeatedIdempotencyKey(){
        String key=UUID.randomUUID().toString();var first=movements.create(request(key,MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,18,20));var second=movements.create(request(key,MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,18,20));
        assertThat(second.id()).isEqualTo(first.id());assertThat(first.recordNo()).startsWith("TR-");assertThat(first.items().getFirst().totalUnits()).isEqualTo(560);assertThat(first.missingPhoto()).isTrue();
    }

    @Test void productionToWarehouseDoesNotStoreManufactureLot(){
        var saved=movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,1,0));
        assertThat(saved.manufactureLot()).isNull();
    }

    @Test void productionTaskLinksFinishedGoodsButCountsOnlyMaterialIssues(){
        var task=productionTasks.create(new ProductionTaskService.SaveRequest(product,1000,LocalDate.of(2026,9,28),"TASK-LOT",null));
        var outboundItem=new MovementDtos.ItemRequest(product,"MAT-OUT",2,0,null,null,List.of());
        movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,
                Instant.parse("2026-09-28T00:00:00Z"),sender,receiver,"MAT-LOT",false,task.id(),null,List.of(outboundItem)));
        var inboundItem=new MovementDtos.ItemRequest(inboundProduct,"FINISHED",2,5,null,null,List.of());
        var inbound=movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,
                Instant.parse("2026-09-28T02:00:00Z"),sender,receiver,null,false,task.id(),null,List.of(inboundItem)));

        var detail=productionTasks.detail(task.id());
        assertThat(detail.completedQuantity()).isEqualTo(60);
        assertThat(detail.progressPercent()).isEqualTo(6);
        assertThat(detail.movements()).hasSize(2);
        assertThat(inbound.productionTaskId()).isEqualTo(task.id());
    }

    @Test void productSearchFiltersByRequiredMovementDirection(){
        assertThat(products.search("",false,ProductUsage.WAREHOUSE_TO_PRODUCTION)).extracting(ProductDtos.Response::id).contains(product).doesNotContain(inboundProduct);
        assertThat(products.search("",false,ProductUsage.PRODUCTION_TO_WAREHOUSE)).extracting(ProductDtos.Response::id).contains(inboundProduct).doesNotContain(product);
    }

    @Test void normalInboundRejectsOutboundProductButReturnAcceptsIt(){
        var item=new MovementDtos.ItemRequest(product,"RETURN-MAT",1,0,null,null,List.of());
        var normal=new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,
                Instant.parse("2026-09-28T01:00:00Z"),sender,receiver,null,false,null,List.of(item));
        assertThatThrownBy(()->movements.create(normal)).hasMessageContaining("不适用于当前流转类型");

        var returned=new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,
                Instant.parse("2026-09-28T01:00:00Z"),sender,receiver," RETURN-LOT ",true,null,List.of(item));
        var saved=movements.create(returned);
        assertThat(saved.returnMovement()).isTrue();
        assertThat(movements.detail(saved.id()).manufactureLot()).isEqualTo("RETURN-LOT");
        assertThat(saved.items().getFirst().productId()).isEqualTo(product);
        var changed=new MovementDtos.SaveRequest(returned.idempotencyKey(),returned.direction(),returned.movementTime(),sender,receiver,"RETURN-LOT-2",true,null,List.of(item));
        movements.update(saved.id(),changed);
        assertThat(movements.detail(saved.id()).manufactureLot()).isEqualTo("RETURN-LOT-2");
        var missing=new MovementDtos.SaveRequest(UUID.randomUUID().toString(),returned.direction(),returned.movementTime(),sender,receiver,null,true,null,List.of(item));
        assertThatThrownBy(()->movements.create(missing)).hasMessageContaining("必须填写物料批次");
    }

    @Test void returnFlagIsRejectedForWarehouseToProduction(){
        var item=new MovementDtos.ItemRequest(product,"MAT",1,0,null,null,List.of());
        var invalid=new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,
                Instant.parse("2026-09-28T01:00:00Z"),sender,receiver,"LOT",true,null,List.of(item));
        assertThatThrownBy(()->movements.create(invalid)).hasMessageContaining("退回只能用于");
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
        var outboundItem=new MovementDtos.ItemRequest(product,"BATCH-BOTH",1,5,null,null,List.of());
        var inboundItem=new MovementDtos.ItemRequest(inboundProduct,"BATCH-IN",1,5,null,null,List.of());
        Instant time=Instant.parse("2026-10-15T01:00:00Z");
        movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,time,sender,receiver,"LOT-BOTH",null,List.of(outboundItem)));
        movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,time,sender,receiver,null,null,List.of(inboundItem)));

        var stats=movements.todayStats(LocalDate.of(2026,10,15),null);

        assertThat(stats.totalCartons()).isEqualTo(2);
        assertThat(stats.totalQuantity()).isEqualTo(70);
        assertThat(stats.products()).hasSize(2);
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
        var unknownProduct=products.create(new ProductDtos.Request("无法清点产品-"+suffix,"UNKNOWN-"+suffix,null,ProductUsage.WAREHOUSE_TO_PRODUCTION,null,null,true));
        var item=new MovementDtos.ItemRequest(unknownProduct.id(),"UNKNOWN-"+suffix,6,0,null,false,null,List.of());
        var request=new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,Instant.parse("2026-09-28T01:00:00Z"),sender,receiver,"LOT-UNKNOWN-PRODUCT",null,List.of(item));

        var saved=movements.create(request);

        assertThat(unknownProduct.quantityUnknown()).isTrue();
        assertThat(unknownProduct.baseUnit()).isNull();
        assertThat(saved.items().getFirst().fullCartons()).isEqualTo(6);
        assertThat(saved.totalCartons()).isEqualTo(6);
        assertThat(saved.items().getFirst().quantityUnknown()).isTrue();
        assertThat(saved.items().getFirst().totalUnits()).isNull();
        assertThat(saved.items().getFirst().calculatedTotalUnits()).isNull();
        var stats=movements.queryStats(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,MovementEnums.Status.ACTIVE,null,null,unknownProduct.name());
        assertThat(stats.directions().getFirst().totalCartons()).isEqualTo(6);
        assertThat(stats.directions().getFirst().unknownItemCount()).isEqualTo(1);
    }

    @Test void preservesKnownCartonsWhenUnknownQuantityIsSelectedAndEdited(){
        var item=new MovementDtos.ItemRequest(product,"KNOWN-CARTONS",4,0,null,true,null,List.of());
        var request=new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,Instant.parse("2026-09-28T01:00:00Z"),sender,receiver,"LOT-UNKNOWN",null,List.of(item));
        var saved=movements.create(request);
        assertThat(saved.totalCartons()).isEqualTo(4);
        assertThat(saved.items().getFirst().totalUnits()).isNull();

        var updatedItem=new MovementDtos.ItemRequest(product,"KNOWN-CARTONS",7,0,null,true,null,List.of());
        var updated=movements.update(saved.id(),new MovementDtos.SaveRequest(request.idempotencyKey(),request.direction(),request.movementTime(),sender,receiver,"LOT-UNKNOWN",null,List.of(updatedItem)));
        var detail=movements.detail(updated.id());
        assertThat(detail.totalCartons()).isEqualTo(7);
        assertThat(detail.items().getFirst().fullCartons()).isEqualTo(7);
        assertThat(detail.items().getFirst().quantityUnknown()).isTrue();
        assertThat(detail.items().getFirst().totalUnits()).isNull();
        assertThat(detail.items().getFirst().calculatedTotalUnits()).isNull();
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

    @Test void statisticsTraceDistinctOriginalMovementsAndTheirOwnMaterialAmounts(){
        var firstItem=new MovementDtos.ItemRequest(product,"BATCH-001",2,0,null,null,List.of());
        var secondItem=new MovementDtos.ItemRequest(product,"BATCH-001",3,0,null,null,List.of());
        var first=movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,Instant.parse("2026-09-28T01:00:00Z"),sender,receiver,"CA202607006",null,List.of(firstItem,secondItem)));
        var single=movements.queryStats(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,MovementEnums.Status.ACTIVE,null,null,first.recordNo()).directions().getFirst().products().getFirst();
        assertThat(single.movements()).hasSize(1);
        assertThat(single.movements().getFirst().id()).isEqualTo(first.id());
        assertThat(single.movements().getFirst().fullCartons()).isEqualTo(5);
        assertThat(single.movements().getFirst().totalQuantity()).isEqualTo(150);

        var second=movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,4,2));
        var third=movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,6,1));
        var voided=movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,99,0));
        movements.voidMovement(voided.id(),"不参与统计");
        movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,Instant.parse("2026-09-29T01:00:00Z"),sender,receiver,"TRACE-OUTSIDE",null,List.of(firstItem)));
        var result=movements.queryStats(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,MovementEnums.Status.ACTIVE,null,null,first.items().getFirst().productName()).directions().getFirst().products().getFirst();
        assertThat(result.movements()).extracting(MovementDtos.StatMovementResponse::id).containsExactlyInAnyOrder(first.id(),second.id(),third.id());
        assertThat(result.fullCartons()).isEqualTo(15);
        assertThat(result.totalQuantity()).isEqualTo(453);
        var source=result.movements().stream().filter(m->m.id().equals(second.id())).findFirst().orElseThrow();
        assertThat(source.fullCartons()).isEqualTo(4);
        assertThat(source.totalQuantity()).isEqualTo(122);
        assertThat(source.recordNo()).isEqualTo(second.recordNo());
        assertThat(source.movementTime()).isEqualTo(second.movementTime());
        assertThat(source.senderName()).isEqualTo(second.senderName());
        assertThat(source.receiverName()).isEqualTo(second.receiverName());
    }

    @Test void materialSummariesSeparateCodesAndBatchesAndPreserveLooseAndOverriddenAmounts(){
        var time=Instant.parse("2026-09-28T03:00:00Z");
        var loose=new MovementDtos.ItemRequest(product,"CODE-A",0,17,null,null,List.of());
        var otherCode=new MovementDtos.ItemRequest(product,"CODE-B",1,2,45L,null,List.of());
        var first=movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,time,sender,receiver,"LOT-A",null,List.of(loose,otherCode)));
        var second=movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,time,sender,receiver,"LOT-B",null,List.of(loose)));
        String name=first.items().getFirst().productName();
        for(String query:List.of(name,"CODE-A","LOT-B")) {
            var page=movements.search(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),null,MovementEnums.Status.ACTIVE,null,null,query,0,1);
            assertThat(page.totalElements()).isEqualTo(query.equals("LOT-B")?1:2);
            assertThat(page.totalPages()).isEqualTo(query.equals("LOT-B")?1:2);
            assertThat(page.content()).hasSize(1);
        }
        var listed=movements.search(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),null,null,null,null,first.recordNo(),0,20).content().getFirst();
        assertThat(listed.items()).hasSize(2);
        var item=listed.items().getFirst();
        assertThat(item.materialCode()).isEqualTo("CODE-A");
        assertThat(item.materialBatch()).isEqualTo("LOT-A");
        assertThat(item.fullCartons()).isZero();
        assertThat(item.looseUnits()).isEqualTo(17);
        assertThat(item.totalQuantity()).isEqualTo(17);
        assertThat(item.baseUnit()).isEqualTo("个");
        var groups=movements.queryStats(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),null,MovementEnums.Status.ACTIVE,null,null,name).directions().getFirst().products();
        assertThat(groups).hasSize(3);
        assertThat(groups).extracting(g->g.materialCode()+"/"+g.materialBatch()).containsExactlyInAnyOrder("CODE-A/LOT-A","CODE-A/LOT-B","CODE-B/LOT-A");
        var adjusted=groups.stream().filter(g->g.materialCode().equals("CODE-B")).findFirst().orElseThrow();
        assertThat(adjusted.totalQuantity()).isEqualTo(45);
        assertThat(adjusted.looseUnits()).isEqualTo(2);
        assertThat(adjusted.movements()).hasSize(1);
        assertThat(adjusted.movements().getFirst().id()).isEqualTo(first.id());
        var batchSearch=movements.queryStats(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),null,MovementEnums.Status.ACTIVE,null,null,"LOT-B").directions().getFirst().products();
        assertThat(batchSearch.stream().filter(g->g.productName().equals(name)).toList()).singleElement().satisfies(g->{
            assertThat(g.movements().getFirst().id()).isEqualTo(second.id());
            assertThat(g.movements().getFirst().looseUnits()).isEqualTo(17);
        });
        var daily=movements.todayStats(LocalDate.of(2026,9,28),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION).products().stream().filter(g->g.productName().equals(name)).toList();
        assertThat(daily).hasSize(3);
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

    @Test void oneProductSupportsMultipleSpecificationsAndKeepsOneSourcePerMovement(){
        var first=packagingItem(1000,3,0,null,0);
        var second=packagingItem(1800,5,0,null,0);
        var saved=movements.create(packagingRequest(List.of(first,second),false));
        assertThat(saved.items()).extracting(MovementDtos.ItemResponse::productId).containsOnly(product);
        assertThat(saved.items()).extracting(MovementDtos.ItemResponse::unitsPerCarton).containsExactly(1000,1800);
        assertThat(saved.items()).extracting(MovementDtos.ItemResponse::totalUnits).containsExactly(3000L,9000L);
        assertThat(saved.totalCartons()).isEqualTo(8);
        assertThat(saved.materials()).hasSize(1);
        assertThat(saved.materials().getFirst().totalQuantity()).isEqualTo(12000);
        assertThat(saved.materials().getFirst().packaging()).hasSize(2);
        var list=movements.search(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),null,null,null,null,saved.recordNo(),0,10).content().getFirst();
        assertThat(list.items()).hasSize(1);
        assertThat(list.totalQuantity()).isEqualTo(12000);
        assertThat(list.items().getFirst().packaging()).containsExactly(new MaterialQuantity.Packaging("FULL",1000L,3),new MaterialQuantity.Packaging("FULL",1800L,5));
        var stat=movements.queryStats(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),null,null,null,null,saved.recordNo()).directions().getFirst().products().getFirst();
        assertThat(stat.totalCartons()).isEqualTo(8);
        assertThat(stat.totalQuantity()).isEqualTo(12000);
        assertThat(stat.packaging()).hasSize(2);
        assertThat(stat.movements()).hasSize(1);
        assertThat(stat.movements().getFirst().packaging()).hasSize(2);
    }

    @Test void tailCartonsAndLooseUnitsAreExplicitAndCountedAcrossEveryReadModel(){
        var saved=movements.create(packagingRequest(List.of(packagingItem(1000,15,1,3L,2)),true));
        var detail=movements.detail(saved.id());
        assertThat(detail.totalCartons()).isEqualTo(16);
        assertThat(detail.items().getFirst().totalUnits()).isEqualTo(15005);
        assertThat(detail.items().getFirst().looseUnits()).isEqualTo(2);
        assertThat(detail.items().getFirst().tailCartons()).isEqualTo(1);
        var list=movements.search(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),null,null,null,null,saved.recordNo(),0,10).content().getFirst();
        assertThat(list.totalCartons()).isEqualTo(16);
        assertThat(list.totalQuantity()).isEqualTo(15005);
        assertThat(list.items().getFirst().packaging()).contains(new MaterialQuantity.Packaging("TAIL",3L,1));
        var stats=movements.queryStats(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),null,null,null,null,saved.recordNo()).directions().getFirst();
        assertThat(stats.totalCartons()).isEqualTo(16);
        assertThat(stats.totalQuantity()).isEqualTo(15005);
        assertThat(stats.products().getFirst().movements().getFirst().tailCartons()).isEqualTo(1);
        var daily=movements.todayStats(LocalDate.of(2026,9,28),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE).products().stream().filter(p->p.materialCode().equals("PACK-"+product)).findFirst().orElseThrow();
        assertThat(daily.totalCartons()).isEqualTo(16);
        assertThat(daily.totalQuantity()).isEqualTo(15005);

        var loose=movements.create(packagingRequest(List.of(packagingItem(1000,15,0,null,3)),true));
        assertThat(loose.totalCartons()).isEqualTo(15);
        assertThat(loose.items().getFirst().totalUnits()).isEqualTo(15003);
        var tail=movements.create(packagingRequest(List.of(packagingItem(1000,15,1,3L,0)),true));
        assertThat(tail.totalCartons()).isEqualTo(16);
        assertThat(tail.items().getFirst().totalUnits()).isEqualTo(15003);
        var onlyTails=movements.create(packagingRequest(List.of(packagingItem(null,0,2,7L,0)),false));
        assertThat(onlyTails.totalCartons()).isEqualTo(2);
        assertThat(onlyTails.items().getFirst().totalUnits()).isEqualTo(14);
    }

    @Test void oldLooseRecordsKeepSnapshotsAndCanBeExplicitlyReclassifiedAsTailCartons(){
        var old=movements.create(request(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,15,3));
        var item=old.items().getFirst();
        var catalog=products.search(item.productName(),true,null).getFirst();
        var updated=products.update(product,new ProductDtos.Request(catalog.name(),catalog.materialCode(),catalog.materialBatch(),ProductUsage.WAREHOUSE_TO_PRODUCTION,1000,"袋",false,List.of(1000,1800,2000)));
        assertThat(products.search(catalog.name(),true,null).getFirst().cartonSpecs()).containsExactly(1000,1800,2000);
        assertThat(updated.id()).isEqualTo(product);
        var unchanged=new MovementDtos.ItemRequest(product,item.batchNo(),15,3,item.totalUnits(),false,"编辑备注",List.of(),null,0,null,item.id());
        var edited=movements.update(old.id(),new MovementDtos.SaveRequest(UUID.randomUUID().toString(),old.direction(),old.movementTime(),sender,receiver,old.manufactureLot(),null,List.of(unchanged)));
        assertThat(edited.totalCartons()).isEqualTo(15);
        assertThat(edited.items().getFirst().unitsPerCarton()).isEqualTo(30);
        assertThat(edited.items().getFirst().baseUnit()).isEqualTo("个");
        assertThat(edited.items().getFirst().tailCartons()).isZero();
        assertThat(edited.items().getFirst().looseUnits()).isEqualTo(3);
        var classified=new MovementDtos.ItemRequest(product,item.batchNo(),15,0,null,false,null,List.of(),30,1,3L,edited.items().getFirst().id());
        var reclassified=movements.update(old.id(),new MovementDtos.SaveRequest(UUID.randomUUID().toString(),old.direction(),old.movementTime(),sender,receiver,old.manufactureLot(),null,List.of(classified)));
        assertThat(reclassified.totalCartons()).isEqualTo(16);
        assertThat(reclassified.items().getFirst().totalUnits()).isEqualTo(453);
        assertThat(reclassified.items().getFirst().looseUnits()).isZero();
        assertThat(reclassified.recordNo()).isEqualTo(old.recordNo());
    }

    @Test void invalidTailInputsAndOverflowAreRejectedWithoutSaving(){
        assertThatThrownBy(()->movements.create(packagingRequest(List.of(packagingItem(1000,0,1,null,0)),false))).hasMessageContaining("尾箱");
        assertThatThrownBy(()->movements.create(packagingRequest(List.of(packagingItem(1000,0,0,3L,0)),false))).hasMessageContaining("尾箱");
        assertThatThrownBy(()->movements.create(packagingRequest(List.of(packagingItem(1000,0,-1,3L,0)),false))).hasMessageContaining("负数");
        assertThatThrownBy(()->movements.create(packagingRequest(List.of(packagingItem(1000,0,2,Long.MAX_VALUE,0)),false))).hasMessageContaining("超出");
        assertThatThrownBy(()->movements.create(packagingRequest(List.of(packagingItem(1,Integer.MAX_VALUE,1,1L,0)),false))).hasMessageContaining("超出");
    }

    @Test void apiRoundTripKeepsPackagingAndRejectsFractionalCartons() throws Exception {
        var session=new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(nz.co.warehouse.auth.AuthController.AUTH_SESSION_KEY,true);
        var request=packagingRequest(List.of(packagingItem(1000,15,1,3L,2)),true);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/movements").session(session)
                .contentType("application/json").content(objectMapper.writeValueAsBytes(request)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalCartons").value(16))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].totalUnits").value(15005))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].tailUnitsPerCarton").value(3))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.materials[0].packaging[1].type").value("TAIL"));
        for(String field:List.of("fullCartons","tailCartons","unitsPerCarton","tailUnitsPerCarton","looseUnits")) {
            var json=objectMapper.valueToTree(request);
            ((com.fasterxml.jackson.databind.node.ObjectNode)json.get("items").get(0)).put(field,1.5);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/movements").session(session)
                    .contentType("application/json").content(objectMapper.writeValueAsBytes(json)))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        }
    }

    private MovementDtos.ItemRequest packagingItem(Integer size,int full,int tail,Long actual,long loose){
        return new MovementDtos.ItemRequest(product,"PACK-"+product,full,loose,null,false,null,List.of(),size,tail,actual,null);
    }
    private MovementDtos.SaveRequest packagingRequest(List<MovementDtos.ItemRequest> items,boolean returned){
        return new MovementDtos.SaveRequest(UUID.randomUUID().toString(),returned?MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE:MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,
                Instant.parse("2026-09-28T04:00:00Z"),sender,receiver,"A001",returned,null,items);
    }

    private MovementDtos.SaveRequest request(String key,MovementEnums.Direction direction,int cartons,long loose){
        long selectedProduct=direction==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION?product:inboundProduct;
        var item=new MovementDtos.ItemRequest(selectedProduct,"BATCH-001",cartons,loose,null,"测试",List.of(new MovementDtos.IssueRequest(MovementEnums.IssueType.MISSING_LABEL,"少贴标签")));
        return new MovementDtos.SaveRequest(key,direction,Instant.parse("2026-09-28T00:00:00Z"),sender,receiver,direction==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION?"CA202607006":null,"整单备注",List.of(item));
    }
}
