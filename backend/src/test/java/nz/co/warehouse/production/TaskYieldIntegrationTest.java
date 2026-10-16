package nz.co.warehouse.production;

import nz.co.warehouse.movement.*;
import nz.co.warehouse.person.*;
import nz.co.warehouse.product.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:warehouse;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"
})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Sql(statements="CREATE TABLE IF NOT EXISTS movement_daily_sequence (sequence_date DATE PRIMARY KEY, next_value INT NOT NULL)")
class TaskYieldIntegrationTest {
    @Autowired ProductionTaskService tasks;
    @Autowired MovementService movements;
    @Autowired ProductService products;
    @Autowired PersonService persons;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    Long raw,inner,output,person;
    @BeforeEach void setup(){
        raw=product(ProductUsage.WAREHOUSE_TO_PRODUCTION,"条");inner=product(ProductUsage.WAREHOUSE_TO_PRODUCTION,"个");
        output=product(ProductUsage.PRODUCTION_TO_WAREHOUSE,"盒");
        person=persons.create(new PersonDtos.Request("Yield-"+UUID.randomUUID(),null)).id();
    }
    Long product(ProductUsage direction,String unit){
        String name="Yield-"+UUID.randomUUID();
        return products.create(new ProductDtos.Request(name,name,null,direction,null,unit,false,List.of(),List.of("22677","22678"))).id();
    }
    ProductionTaskService.SaveRequest plan(long quantity,int units,ProductionTask.OutputQuantityBasis basis){
        return new ProductionTaskService.SaveRequest(null,null,LocalDate.now(),"TASK-LOT",null,
            List.of(new ProductionTaskService.TargetRequest(raw,quantity),new ProductionTaskService.TargetRequest(inner,5000)),output,null,"Yield task",null,28,units,basis,raw);
    }
    MovementDtos.DetailResponse move(Long task,Long product,String batch,long quantity,boolean returned){
        return movement(task,product,batch,0,0,null,quantity,null,returned);
    }
    MovementDtos.DetailResponse movement(Long task,Long product,String batch,int full,int tails,Long tailQuantity,long loose,Integer units,boolean returned){
        var direction=returned||product.equals(output)?MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE:MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION;
        return movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),direction,Instant.now(),person,person,null,returned,task,null,
            List.of(new MovementDtos.ItemRequest(product,"CODE",full,loose,null,false,null,List.of(),units,tails,tailQuantity,null,null,batch))));
    }
    @Test void quickLinkPreservesRecordAndUpdatesTaskAndList(){
        var task=tasks.create(plan(20000,1,ProductionTask.OutputQuantityBasis.UNIT));
        var record=movements.detail(move(null,raw,"22677",1200,false).id());
        var linked=movements.linkTask(record.id(),task.id());
        assertThat(linked.productionTaskId()).isEqualTo(task.id());
        assertThat(linked.items()).isEqualTo(record.items());
        assertThat(linked.movementTime()).isEqualTo(record.movementTime());
        assertThat(tasks.detail(task.id()).targets().getFirst().issuedQuantity()).isEqualTo(1200);
        assertThat(movements.search(LocalDate.now().minusDays(1),LocalDate.now().plusDays(1),null,null,null,null,"",0,100).content())
            .filteredOn(row->row.id().equals(record.id())).singleElement()
            .satisfies(row->{assertThat(row.productionTaskId()).isEqualTo(task.id());assertThat(row.productionTaskName()).isEqualTo(task.name());});
        assertThatThrownBy(()->movements.linkTask(record.id(),task.id())).hasMessageContaining("已关联");
        var voided=move(null,raw,"22677",100,false);
        movements.voidMovement(voided.id(),"test");
        assertThatThrownBy(()->movements.linkTask(voided.id(),task.id()));
    }
    @Test void quickLinkEndpointRequiresLogin() throws Exception {
        var task=tasks.create(plan(20000,1,ProductionTask.OutputQuantityBasis.UNIT));
        var record=move(null,raw,"22677",1200,false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/movements/"+record.id()+"/production-task").param("taskId",task.id().toString()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        assertThat(movements.detail(record.id()).productionTaskId()).isNull();
    }
    @Test void demandCombinesBatchesAndNewTaskStartsImmediately(){
        var task=tasks.create(plan(20000,1,ProductionTask.OutputQuantityBasis.UNIT));
        assertThat(task.status()).isEqualTo(ProductionTask.Status.IN_PROGRESS);
        move(task.id(),inner,"22677",2000,false);move(task.id(),inner,"22678",3100,false);
        var target=tasks.detail(task.id()).targets().get(1);
        assertThat(target.completedQuantity()).isEqualTo(5100);
        assertThat(target.progressPercent()).isEqualTo(100);
        assertThat(target.batches()).extracting(ProductionTaskService.BatchResponse::netQuantity).containsExactly(2000L,3100L);
    }
    @Test void taskHistoryKeepsEachDeliveryPackagingAndDirection(){
        var task=tasks.create(plan(20000,30,ProductionTask.OutputQuantityBasis.BOX));
        var issued=movement(task.id(),raw,"22677",2,1,20L,5,100,false);
        move(task.id(),raw,"22678",300,false);
        move(task.id(),raw,"22677",10,true);
        movement(task.id(),output,"22678",3,1,4L,0,28,false);
        var rows=tasks.detail(task.id()).movements();
        assertThat(rows).hasSize(4);
        var delivery=rows.stream().filter(m->m.id().equals(issued.id())).findFirst().orElseThrow();
        assertThat(delivery.direction()).isEqualTo(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION);
        var item=delivery.items().getFirst();
        assertThat(item.productId()).isEqualTo(raw);
        assertThat(item.materialBatch()).isEqualTo("22677");
        assertThat(item.totalCartons()).isEqualTo(3);
        assertThat(item.totalQuantity()).isEqualTo(225);
        assertThat(item.looseUnits()).isEqualTo(5);
        assertThat(item.packaging()).containsExactly(new MaterialQuantity.Packaging("FULL",100L,2),new MaterialQuantity.Packaging("TAIL",20L,1));
        assertThat(rows.stream().filter(ProductionTaskService.MovementResponse::returnMovement)).hasSize(1);
        assertThat(rows.stream().filter(m->m.direction()==MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE&&!m.returnMovement()).findFirst().orElseThrow().items().getFirst().productId()).isEqualTo(output);
    }
    @Test void deletingTaskPreservesActiveAndVoidedMovements(){
        var task=tasks.create(plan(20000,30,ProductionTask.OutputQuantityBasis.BOX));
        var issue=move(task.id(),raw,"22677",1000,false);
        var receipt=move(task.id(),output,"22678",10,false);
        movements.voidMovement(receipt.id(),"correction");
        tasks.delete(task.id());
        assertThatThrownBy(()->tasks.detail(task.id())).hasMessageContaining("找不到");
        assertThat(tasks.list(null)).extracting(ProductionTaskService.Response::id).doesNotContain(task.id());
        assertThat(movements.detail(issue.id()).productionTaskId()).isNull();
        assertThat(movements.detail(issue.id()).items().getFirst().totalUnits()).isEqualTo(1000);
        assertThat(movements.detail(receipt.id()).productionTaskId()).isNull();
        assertThat(movements.detail(receipt.id()).status()).isEqualTo(MovementEnums.Status.VOID);
    }
    @Test void taskDeleteEndpointRequiresLoginAndReturnsNoContent() throws Exception {
        var task=tasks.create(plan(20000,30,ProductionTask.OutputQuantityBasis.BOX));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/production-tasks/"+task.id()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        assertThat(tasks.detail(task.id()).id()).isEqualTo(task.id());
        var session=new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(nz.co.warehouse.auth.AuthController.AUTH_SESSION_KEY,true);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/production-tasks/"+task.id()).session(session))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        assertThatThrownBy(()->tasks.detail(task.id())).hasMessageContaining("找不到");
    }
    @Test void productionAndLossUseActualNetIssuesRatherThanPlannedDemand(){
        var task=tasks.create(plan(20000,1,ProductionTask.OutputQuantityBasis.UNIT));
        move(task.id(),raw,"22677",10000,false);move(task.id(),output,"22678",9000,false);
        var result=tasks.detail(task.id());
        assertThat(result.targets().getFirst().progressPercent()).isEqualTo(50);
        assertThat(result.output().productionPercent()).isEqualByComparingTo("90");
        assertThat(result.output().lossPercent()).isEqualByComparingTo("10");
        move(task.id(),output,"22677",1000,false);
        result=tasks.detail(task.id());
        assertThat(result.targets().getFirst().progressPercent()).isEqualTo(50);
        assertThat(result.output().productionPercent()).isEqualByComparingTo("100");
        assertThat(result.output().lossPercent()).isEqualByComparingTo("0");
        assertThat(result.status()).isEqualTo(ProductionTask.Status.IN_PROGRESS);
        assertThat(tasks.status(task.id(),ProductionTask.Status.COMPLETED).output().provisional()).isFalse();
        assertThatThrownBy(()->move(task.id(),raw,"22677",1,false)).hasMessageContaining("已结束");
        tasks.status(task.id(),ProductionTask.Status.IN_PROGRESS);
        assertThatCode(()->move(task.id(),raw,"22677",1,false)).doesNotThrowAnyException();
    }
    @Test void twentyEightBoxesWithThirtyUnitsIncludesThreeBoxTail(){
        var task=tasks.create(plan(120000,30,ProductionTask.OutputQuantityBasis.BOX));
        move(task.id(),raw,"22677",120000,false);
        var receipt=movement(task.id(),output,"22678",142,1,3L,0,28,false);
        assertThat(receipt.totalCartons()).isEqualTo(143);
        assertThat(receipt.items().getFirst().totalUnits()).isEqualTo(3979);
        var result=tasks.detail(task.id()).output();
        assertThat(result.receivedBoxes()).isEqualByComparingTo("3979");
        assertThat(result.producedQuantity()).isEqualByComparingTo("119370");
        assertThat(result.productionPercent()).isEqualByComparingTo("99.48");
        assertThat(result.lossPercent()).isEqualByComparingTo("0.52");
        assertThatThrownBy(()->tasks.update(task.id(),plan(120000,20,ProductionTask.OutputQuantityBasis.BOX))).hasMessageContaining("包装换算");
    }
    @Test void unitBasedReceiptsAndReturnsUseCorrectDirectionsAndVoidsAreExcluded(){
        var task=tasks.create(plan(20000,30,ProductionTask.OutputQuantityBasis.UNIT));
        move(task.id(),raw,"22677",10000,false);
        var returned=move(task.id(),raw,"22678",1000,true);
        var receipt=movement(task.id(),output,"22677",10,1,90L,510,840,false);
        assertThat(receipt.items().getFirst().totalUnits()).isEqualTo(9000);
        assertThat(tasks.detail(task.id()).output().productionPercent()).isEqualByComparingTo("100");
        assertThat(tasks.detail(task.id()).output().netIssuedQuantity()).isEqualTo(9000);
        movements.voidMovement(returned.id(),"correction");
        assertThat(tasks.detail(task.id()).output().productionPercent()).isEqualByComparingTo("90");
        movements.voidMovement(receipt.id(),"correction");
        assertThat(tasks.detail(task.id()).output().receivedQuantity()).isZero();
        assertThat(tasks.linkableBatches(new ProductionTaskService.LinkRequest(List.of(new ProductionTaskService.BatchSelection(raw,"22677")),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,true))).extracting(ProductionTaskService.LinkOption::id).contains(task.id());
        assertThat(tasks.linkableBatches(new ProductionTaskService.LinkRequest(List.of(new ProductionTaskService.BatchSelection(inner,"22677")),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,false))).extracting(ProductionTaskService.LinkOption::id).doesNotContain(task.id());
        assertThatThrownBy(()->move(task.id(),output,"22677",3,true)).hasMessageContaining("不适用于");
    }
    @Test void noIssuesDoesNotReportFalseProductionOrLossRates(){
        var task=tasks.create(plan(20000,30,ProductionTask.OutputQuantityBasis.BOX));
        assertThat(task.output().productionPercent()).isNull();
        assertThat(task.output().lossPercent()).isNull();
        move(task.id(),output,"22677",3,false);
        assertThat(tasks.detail(task.id()).output().productionPercent()).isNull();
    }
    @Test void legacyBatchTargetsAggregateOnceAndCanBeEditedWithoutLosingMovements(){
        var task=tasks.create(new ProductionTaskService.SaveRequest(raw,2000,LocalDate.now(),"OLD",null));
        jdbc.update("update production_task_target set material_batch='22677' where production_task_id=?",task.id());
        jdbc.update("insert into production_task_target(production_task_id,product_id,material_batch,target_quantity,sort_order) values(?,?,'22678',3000,1)",task.id(),raw);
        move(task.id(),raw,"22677",2000,false);move(task.id(),raw,"22678",3100,false);
        var target=tasks.detail(task.id()).targets().getFirst();
        assertThat(target.targetQuantity()).isEqualTo(5000);
        assertThat(target.completedQuantity()).isEqualTo(5100);
        tasks.update(task.id(),new ProductionTaskService.SaveRequest(raw,5000,LocalDate.now(),"OLD",null));
        assertThat(tasks.detail(task.id()).targets()).singleElement().satisfies(t->assertThat(t.completedQuantity()).isEqualTo(5100));
        assertThat(tasks.detail(task.id()).movements()).hasSize(2);
    }
    @Test void outputCanBeAddedLaterAndRequiresAValidPrimaryMaterial(){
        var task=tasks.create(new ProductionTaskService.SaveRequest(raw,20000,LocalDate.now(),"LATER",null));
        assertThat(tasks.linkableBatches(new ProductionTaskService.LinkRequest(List.of(new ProductionTaskService.BatchSelection(output,"22677")),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,false))).extracting(ProductionTaskService.LinkOption::id).doesNotContain(task.id());
        assertThat(tasks.update(task.id(),plan(20000,30,ProductionTask.OutputQuantityBasis.BOX)).output()).isNotNull();
        assertThat(tasks.linkableBatches(new ProductionTaskService.LinkRequest(List.of(new ProductionTaskService.BatchSelection(output,"22678")),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,false))).extracting(ProductionTaskService.LinkOption::id).contains(task.id());
    }
    @Test void unknownPrimaryQuantitySuppressesPercentagesInsteadOfShowingFalseLoss(){
        var task=tasks.create(plan(20000,30,ProductionTask.OutputQuantityBasis.BOX));
        move(task.id(),raw,"22677",10000,false);move(task.id(),output,"22677",300,false);
        movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,Instant.now(),person,person,null,false,task.id(),null,
            List.of(new MovementDtos.ItemRequest(raw,"CODE",0,0,null,true,null,List.of(),null,0,null,null,null,"22678"))));
        assertThat(tasks.detail(task.id()).output().productionPercent()).isNull();
        assertThat(tasks.detail(task.id()).output().lossPercent()).isNull();
        assertThat(tasks.detail(task.id()).targets().getFirst().unknownQuantityCount()).isEqualTo(1);
    }
    @Test void invalidOrIncompletePackingCannotBeSaved(){
        var targets=List.of(new ProductionTaskService.TargetRequest(raw,20000));
        assertThatThrownBy(()->tasks.create(new ProductionTaskService.SaveRequest(null,null,LocalDate.now(),"INVALID",null,targets,output,null,"Invalid",null,28,null,ProductionTask.OutputQuantityBasis.UNIT,raw))).hasMessageContaining("每盒数量");
        assertThatThrownBy(()->tasks.create(new ProductionTaskService.SaveRequest(null,null,LocalDate.now(),"INVALID",null,targets,output,null,"Invalid",null,28,30,ProductionTask.OutputQuantityBasis.UNIT,inner))).hasMessageContaining("主料");
        assertThatThrownBy(()->tasks.create(new ProductionTaskService.SaveRequest(null,null,LocalDate.now(),"INVALID",null,targets,output,null,"Invalid",null,Integer.MAX_VALUE,30,ProductionTask.OutputQuantityBasis.UNIT,raw))).hasMessageContaining("超出");
    }
}
