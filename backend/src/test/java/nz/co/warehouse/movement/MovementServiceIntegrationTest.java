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
@Sql(statements="CREATE TABLE IF NOT EXISTS movement_daily_sequence (sequence_date DATE PRIMARY KEY, next_value INT NOT NULL)", executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MovementServiceIntegrationTest {
    @Autowired MovementService movements;
    @Autowired ProductService products;
    @Autowired PersonService persons;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductionTaskService productionTasks;
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

    @Test void rangeStatsMergeDifferentPackagingNamesOnlyWhenSavedCodeAndLotBothMatch(){
        String code="MERGE-"+UUID.randomUUID();
        var small=products.create(new ProductDtos.Request("WhatAPoo1400-"+code,"DEFAULT-A-"+code,"DEFAULT-LOT",ProductUsage.WAREHOUSE_TO_PRODUCTION,1400,"个"));
        var large=products.create(new ProductDtos.Request("WhatAPoo1750-"+code,"DEFAULT-B-"+code,"OTHER-DEFAULT-LOT",ProductUsage.WAREHOUSE_TO_PRODUCTION,1750,"个"));
        var first=statsMovement("26020053",new MovementDtos.ItemRequest(small.id(),code,1,0,null,null,List.of()),
                new MovementDtos.ItemRequest(large.id(),code,1,0,null,null,List.of()),new MovementDtos.ItemRequest(small.id(),code,0,10,null,null,List.of()));
        var second=statsMovement("26020053",new MovementDtos.ItemRequest(large.id(),code,2,0,null,null,List.of()));
        var unknown=statsMovement("26020053",new MovementDtos.ItemRequest(small.id(),code,1,0,null,true,null,List.of()));
        statsMovement("21867",new MovementDtos.ItemRequest(large.id(),code,2,0,null,null,List.of()));
        statsMovement("26020053",new MovementDtos.ItemRequest(small.id(),code+"-OTHER",1,0,null,null,List.of()));
        var voided=statsMovement("26020053",new MovementDtos.ItemRequest(small.id(),code,99,0,null,null,List.of()));
        movements.voidMovement(voided.id(),"duplicate");
        var stats=movements.queryStats(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,MovementEnums.Status.ACTIVE,null,null,code);
        var groups=stats.directions().getFirst().products();
        assertThat(groups).hasSize(3);
        var merged=groups.stream().filter(g->code.equals(g.materialCode())&&"26020053".equals(g.materialBatch())).findFirst().orElseThrow();
        assertThat(merged.productName()).contains(small.name(),large.name());
        assertThat(merged.fullCartons()).isEqualTo(5);
        assertThat(merged.totalQuantity()).isEqualTo(6660);
        assertThat(merged.unknownItemCount()).isEqualTo(1);
        assertThat(merged.movements()).extracting(MovementDtos.StatMovementResponse::id).containsExactlyInAnyOrder(first.id(),second.id(),unknown.id());
        var firstSource=merged.movements().stream().filter(m->m.id().equals(first.id())).findFirst().orElseThrow();
        assertThat(firstSource.fullCartons()).isEqualTo(2);
        assertThat(firstSource.totalQuantity()).isEqualTo(3160);
        var sameCodeDifferentLot=groups.stream().filter(g->"21867".equals(g.materialBatch())).findFirst().orElseThrow();
        assertThat(sameCodeDifferentLot.totalQuantity()).isEqualTo(3500);
        assertThat(stats.directions().getFirst().totalQuantity()).isEqualTo(11560);
    }

    @Test void rangeStatsDoNotMergeDifferentNamesWhenMaterialLotIsMissing(){
        String code="NO-LOT-"+UUID.randomUUID();
        var other=products.create(new ProductDtos.Request("Other-"+code,code,null,ProductUsage.PRODUCTION_TO_WAREHOUSE,20,"个"));
        var items=List.of(new MovementDtos.ItemRequest(inboundProduct,code,1,0,null,null,List.of()),new MovementDtos.ItemRequest(other.id(),code,1,0,null,null,List.of()));
        var movement=movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,Instant.parse("2026-09-28T01:00:00Z"),sender,receiver,null,null,items));
        var groups=movements.queryStats(LocalDate.of(2026,9,28),LocalDate.of(2026,9,28),null,MovementEnums.Status.ACTIVE,null,null,movement.recordNo()).directions().getFirst().products();
        assertThat(groups).hasSize(2);
        assertThat(groups).allMatch(g->g.materialBatch()==null);
    }

    private MovementDtos.DetailResponse statsMovement(String lot,MovementDtos.ItemRequest... items){
        return movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,
                Instant.parse("2026-09-28T01:00:00Z"),sender,receiver,lot,null,List.of(items)));
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
        long selectedProduct=direction==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION?product:inboundProduct;
        var item=new MovementDtos.ItemRequest(selectedProduct,"BATCH-001",cartons,loose,null,"测试",List.of(new MovementDtos.IssueRequest(MovementEnums.IssueType.MISSING_LABEL,"少贴标签")));
        return new MovementDtos.SaveRequest(key,direction,Instant.parse("2026-09-28T00:00:00Z"),sender,receiver,direction==MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION?"CA202607006":null,"整单备注",List.of(item));
    }
}
