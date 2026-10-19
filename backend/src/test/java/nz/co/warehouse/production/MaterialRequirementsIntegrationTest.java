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
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Sql(statements="CREATE TABLE IF NOT EXISTS movement_daily_sequence (sequence_date DATE PRIMARY KEY, next_value INT NOT NULL)")
class MaterialRequirementsIntegrationTest {
    @Autowired ProductionTaskService tasks;
    @Autowired MovementService movements;
    @Autowired ProductService products;
    @Autowired PersonService persons;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    Long inner,outer,drink,person;

    // Legacy VOID rows still exist even though the public operation is now delete.
    private void historicalVoid(long id,String reason){
        jdbc.update("UPDATE movement SET status='VOID', void_reason=? WHERE id=?",reason,id);
    }
    @BeforeEach void setup(){
        inner=product("Inner",ProductUsage.WAREHOUSE_TO_PRODUCTION);
        outer=product("Outer",ProductUsage.WAREHOUSE_TO_PRODUCTION);
        drink=product("Drink",ProductUsage.PRODUCTION_TO_WAREHOUSE);
        person=persons.create(new PersonDtos.Request("Person-"+UUID.randomUUID(),null)).id();
    }

    @Test void reportsMissingMaterialsForTwelveThousandDrinksAndIgnoresFinishedGoods(){
        var task=tasks.create(request(target(inner,400),target(outer,400)));
        move(task.id(),inner,300L,false,false);
        move(task.id(),outer,400L,false,false);
        move(task.id(),drink,12000L,false,false);
        var detail=tasks.detail(task.id());
        assertThat(detail.targets()).extracting(ProductionTaskService.TargetResponse::shortageQuantity).containsExactly(100L,0L);
        assertThat(detail.targets()).extracting(ProductionTaskService.TargetResponse::completedQuantity).containsExactly(300L,400L);
        assertThat(detail.progressPercent()).isEqualTo(87);
        assertThat(detail.movements()).hasSize(3);
        move(task.id(),outer,50L,true,false);
        var returned=tasks.detail(task.id()).targets().get(1);
        assertThat(returned.issuedQuantity()).isEqualTo(400);
        assertThat(returned.returnedQuantity()).isEqualTo(50);
        assertThat(returned.shortageQuantity()).isEqualTo(50);
        move(task.id(),inner,100L,false,false);
        move(task.id(),outer,50L,false,false);
        assertThat(tasks.detail(task.id()).progressPercent()).isEqualTo(100);
    }

    @Test void onlyActiveLinkedMaterialMovementsCountAndUnknownQuantitiesAreFlagged(){
        var task=tasks.create(request(target(inner,400),target(outer,400)));
        var other=tasks.create(request(target(inner,400)));
        move(other.id(),inner,400L,false,false);
        var voided=move(task.id(),inner,400L,false,false);
        historicalVoid(voided.id(),"duplicate");
        move(task.id(),inner,null,false,true);
        move(task.id(),outer,800L,false,false);
        move(task.id(),outer,null,true,true);
        var detail=tasks.detail(task.id());
        assertThat(detail.targets()).extracting(ProductionTaskService.TargetResponse::shortageQuantity).containsExactly(400L,0L);
        assertThat(detail.targets()).extracting(ProductionTaskService.TargetResponse::unknownQuantityCount).containsExactly(1,1);
        assertThat(detail.progressPercent()).isEqualTo(50);
        assertThat(tasks.list(null).stream().filter(t->t.id().equals(task.id())).findFirst().orElseThrow().targets()).hasSize(2);
    }

    @Test void editingReordersAndRemovesTargetsWithoutLosingMaterialHistory(){
        var task=tasks.create(request(target(inner,400)));
        move(task.id(),inner,100L,false,false);
        tasks.update(task.id(),request(target(outer,400),target(inner,200)));
        var detail=tasks.detail(task.id());
        assertThat(detail.targets()).extracting(ProductionTaskService.TargetResponse::productId).containsExactly(outer,inner);
        assertThat(detail.targets().get(1).shortageQuantity()).isEqualTo(100);
        assertThatThrownBy(()->products.delete(outer)).hasMessageContaining("不能删除");
        tasks.update(task.id(),request(target(inner,200)));
        assertThat(tasks.detail(task.id()).movements()).hasSize(1);
        assertThatCode(()->products.delete(outer)).doesNotThrowAnyException();
    }

    @Test void rejectsInboundTargetsOnCreateAndUpdateAndValidatesMaterialRequirements(){
        assertThatThrownBy(()->tasks.create(request(target(drink,400)))).hasMessageContaining("不适用于");
        assertThatThrownBy(()->tasks.create(request())).hasMessageContaining("至少添加");
        assertThatThrownBy(()->tasks.create(request(target(inner,400),target(inner,400)))).hasMessageContaining("重复");
        assertThatThrownBy(()->tasks.create(request(target(inner,0)))).hasMessageContaining("大于零");
        var task=tasks.create(request(target(inner,400)));
        assertThatThrownBy(()->tasks.update(task.id(),request(target(drink,400)))).hasMessageContaining("不适用于");
        products.setActive(inner,false);
        assertThatCode(()->tasks.update(task.id(),request(target(inner,500)))).doesNotThrowAnyException();
        assertThatThrownBy(()->tasks.create(request(target(inner,400)))).hasMessageContaining("停用");
        tasks.status(task.id(),ProductionTask.Status.COMPLETED);
        assertThatThrownBy(()->move(task.id(),outer,20L,false,false)).hasMessageContaining("已结束");
    }

    @Test void legacyFinishedGoodsTargetsStayReadableAndMustBeCorrectedWhenEditing(){
        var task=tasks.create(request(target(inner,400)));
        jdbc.update("update production_task set product_id=? where id=?",drink,task.id());
        jdbc.update("update production_task_target set product_id=? where production_task_id=?",drink,task.id());
        move(task.id(),drink,12000L,false,false);
        var legacy=tasks.detail(task.id());
        assertThat(legacy.targets().getFirst().materialTarget()).isFalse();
        assertThat(legacy.progressPercent()).isZero();
        assertThatThrownBy(()->tasks.update(task.id(),request(target(drink,400)))).hasMessageContaining("不适用于");
        tasks.update(task.id(),request(target(inner,400),target(outer,400)));
        assertThat(tasks.detail(task.id()).targets()).allMatch(ProductionTaskService.TargetResponse::materialTarget);
        assertThat(tasks.detail(task.id()).movements()).hasSize(1);
    }

    @Test void returnsCanReopenShortageAndVoidingThemRestoresReadiness(){
        var task=tasks.create(request(target(inner,400)));
        move(task.id(),inner,400L,false,false);
        var returned=move(task.id(),inner,450L,true,false);
        var target=tasks.detail(task.id()).targets().getFirst();
        assertThat(target.completedQuantity()).isEqualTo(-50);
        assertThat(target.shortageQuantity()).isEqualTo(450);
        assertThat(target.progressPercent()).isZero();
        historicalVoid(returned.id(),"incorrect return");
        assertThat(tasks.detail(task.id()).targets().getFirst().shortageQuantity()).isZero();
    }

    @Test void filtersByEverySelectedMaterialAndAllowsIssuesWhileInProgress(){
        var both=tasks.create(request(target(inner,500),target(outer,500)));
        var one=tasks.create(request(target(inner,500)));
        assertThat(tasks.linkable(List.of(inner,outer),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,false))
                .extracting(ProductionTaskService.LinkOption::id).contains(both.id()).doesNotContain(one.id());
        tasks.status(both.id(),ProductionTask.Status.IN_PROGRESS);
        assertThat(tasks.linkable(List.of(inner),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,false))
                .extracting(ProductionTaskService.LinkOption::id).contains(one.id(),both.id());
        assertThatCode(()->move(both.id(),inner,10L,false,false)).doesNotThrowAnyException();
        assertThatThrownBy(()->move(one.id(),outer,10L,false,false)).hasMessageContaining("不匹配");
        assertThat(tasks.linkable(List.of(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,false)).isEmpty();
    }

    @Test void countsPartialOutputAndExpectedPackagingWhileStillPreparing(){
        Long raw=product("RawDrink",ProductUsage.WAREHOUSE_TO_PRODUCTION);
        var task=tasks.create(new ProductionTaskService.SaveRequest(null,null,LocalDate.now(),"500-BOXES",null,
                List.of(target(raw,15000),target(inner,500),target(outer,500)),drink,500L));
        move(task.id(),raw,15000L,false,false);
        move(task.id(),inner,500L,false,false);
        move(task.id(),outer,500L,false,false);
        move(task.id(),drink,100L,false,false);
        var result=tasks.detail(task.id());
        assertThat(result.status()).isEqualTo(ProductionTask.Status.IN_PROGRESS);
        assertThat(result.progressPercent()).isEqualTo(100);
        assertThat(result.output().progressPercent()).isEqualTo(20);
        assertThat(result.output().receivedQuantity()).isEqualTo(100);
        assertThat(result.targets().getFirst().estimatedUsedQuantity()).isEqualByComparingTo("3000");
        assertThat(result.targets().get(1).estimatedUsedQuantity()).isEqualByComparingTo("100");
        assertThat(result.targets().get(2).estimatedUsedQuantity()).isEqualByComparingTo("100");
        move(task.id(),drink,400L,false,false);
        assertThat(tasks.detail(task.id()).output().progressPercent()).isEqualTo(100);
        assertThat(tasks.detail(task.id()).targets().getFirst().estimatedUsedQuantity()).isEqualByComparingTo("15000");
    }

    @Test void outputMeasuredInIndividualUnitsUsesTheConfiguredRatio(){
        var task=tasks.create(request(target(inner,500),target(outer,500)));
        // A plan of 12,000 individual drinks and 500 boxes is intentionally not a carton-size conversion.
        move(task.id(),drink,2400L,false,false);
        assertThat(tasks.detail(task.id()).output().progressPercent()).isEqualTo(20);
        assertThat(tasks.detail(task.id()).targets().getFirst().estimatedUsedQuantity()).isEqualByComparingTo("100");
    }

    @Test void completedTasksAcceptMatchingReceiptsAndReturnsButNeverNewIssues(){
        var task=tasks.create(request(target(inner,400)));
        assertThat(tasks.status(task.id(),ProductionTask.Status.COMPLETED).status()).isEqualTo(ProductionTask.Status.COMPLETED);
        assertThat(tasks.linkable(List.of(inner),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,false)).isEmpty();
        assertThat(tasks.linkable(List.of(drink),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,false))
                .extracting(ProductionTaskService.LinkOption::id).contains(task.id());
        assertThatCode(()->move(task.id(),drink,100L,false,false)).doesNotThrowAnyException();
        assertThatCode(()->move(task.id(),inner,10L,true,false)).doesNotThrowAnyException();
        assertThatThrownBy(()->move(task.id(),inner,10L,false,false)).hasMessageContaining("已结束");
        tasks.status(task.id(),ProductionTask.Status.CANCELLED);
        assertThatThrownBy(()->move(task.id(),drink,100L,false,false)).hasMessageContaining("已结束");
        assertThat(tasks.linkable(List.of(drink),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,false))
                .extracting(ProductionTaskService.LinkOption::id).doesNotContain(task.id());
    }

    @Test void outputChangesOnEditVoidDeleteAndRelinkAndUnknownIsNotZeroCompletion(){
        var first=tasks.create(request(target(inner,400)));
        var second=tasks.create(request(target(inner,400)));
        var receipt=move(first.id(),drink,1200L,false,false);
        move(first.id(),drink,null,false,true);
        var item=new MovementDtos.ItemRequest(drink,"LOT",0,0,2400L,false,"partial",List.of());
        var changed=new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,
                Instant.now(),person,person,null,false,second.id(),null,List.of(item));
        movements.update(receipt.id(),changed);
        assertThat(tasks.detail(first.id()).output().receivedQuantity()).isZero();
        assertThat(tasks.detail(first.id()).output().unknownQuantityCount()).isEqualTo(1);
        assertThat(tasks.detail(second.id()).output().receivedQuantity()).isEqualTo(2400);
        historicalVoid(receipt.id(),"duplicate");
        assertThat(tasks.detail(second.id()).output().receivedQuantity()).isZero();
        var deleted=move(second.id(),drink,1200L,false,false);
        movements.delete(deleted.id());
        assertThat(tasks.detail(second.id()).output().receivedQuantity()).isZero();
    }

    @Test void multiplePackingRowsCountTheirFullTailAndLooseQuantitiesOnce(){
        var task=tasks.create(request(target(inner,400)));
        var full=new MovementDtos.ItemRequest(drink,"CODE",15,2,null,false,null,List.of(),1000,1,3L,null);
        var other=new MovementDtos.ItemRequest(drink,"CODE",5,0,null,false,null,List.of(),1800,0,null,null);
        var saved=movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,
                Instant.now(),person,person,null,false,task.id(),null,List.of(full,other)));
        assertThat(saved.totalCartons()).isEqualTo(21);
        assertThat(tasks.detail(task.id()).output().receivedQuantity()).isEqualTo(24005);
        assertThat(tasks.detail(task.id()).output().progressPercent()).isEqualTo(100);
        assertThat(tasks.detail(task.id()).status()).isEqualTo(ProductionTask.Status.IN_PROGRESS);
    }

    @Test void validatesOutputIdentityAndProtectsProductAndExistingReceiptHistory(){
        var task=tasks.create(request(target(inner,400)));
        Long unrelated=product("OtherOutput",ProductUsage.PRODUCTION_TO_WAREHOUSE);
        assertThat(tasks.linkable(List.of(unrelated),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,false)).isEmpty();
        var wrongReceipt=new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,
                Instant.now(),person,person,null,false,task.id(),null,List.of(
                    new MovementDtos.ItemRequest(drink,"CODE",0,1,null,false,null,List.of()),
                    new MovementDtos.ItemRequest(unrelated,"OTHER",0,1,null,false,null,List.of())));
        assertThatThrownBy(()->movements.create(wrongReceipt)).hasMessageContaining("不匹配");
        assertThatThrownBy(()->products.delete(drink)).hasMessageContaining("不能删除");
        move(task.id(),drink,100L,false,false);
        assertThatThrownBy(()->tasks.update(task.id(),new ProductionTaskService.SaveRequest(null,null,LocalDate.now(),"LOT",null,
                List.of(target(inner,400)),unrelated,12000L))).hasMessageContaining("不能更换");
        assertThatThrownBy(()->tasks.create(new ProductionTaskService.SaveRequest(null,null,LocalDate.now(),"LOT",null,
                List.of(target(inner,400)),drink,null))).hasMessageContaining("同时填写");
        assertThatThrownBy(()->tasks.create(new ProductionTaskService.SaveRequest(null,null,LocalDate.now(),"LOT",null,
                List.of(target(inner,400)),inner,100L))).hasMessageContaining("不适用于");
    }

    @Test void oldTasksRemainReadableAndExistingLinksRemainEditableAfterCompletion(){
        var task=tasks.create(new ProductionTaskService.SaveRequest(inner,400,LocalDate.now(),"LEGACY",null));
        assertThat(task.output()).isNull();
        var saved=move(task.id(),inner,100L,false,false);
        tasks.status(task.id(),ProductionTask.Status.COMPLETED);
        var item=new MovementDtos.ItemRequest(inner,"LOT",0,0,120L,false,"corrected",List.of());
        movements.update(saved.id(),new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,
                Instant.now(),person,person,"LOT",false,task.id(),"legacy note",List.of(item)));
        assertThat(tasks.detail(task.id()).targets().getFirst().issuedQuantity()).isEqualTo(120);
        assertThat(tasks.detail(task.id()).output()).isNull();
        assertThat(tasks.linkable(List.of(drink),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,false))
                .extracting(ProductionTaskService.LinkOption::id).doesNotContain(task.id());
        assertThatThrownBy(()->move(task.id(),drink,10L,false,false)).hasMessageContaining("不匹配");
    }

    @Test void apiFiltersTasksAndDirectCompletionRequiresAuthentication() throws Exception {
        var task=tasks.create(request(target(inner,400)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/production-tasks/linkable")
                .param("productIds",inner.toString()).param("direction","WAREHOUSE_TO_PRODUCTION"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].id").value(task.id()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/production-tasks/"+task.id()+"/status")
                .param("status","COMPLETED")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        var session=new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(nz.co.warehouse.auth.AuthController.AUTH_SESSION_KEY,true);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/production-tasks/"+task.id()+"/status")
                .session(session).param("status","COMPLETED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("COMPLETED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/production-tasks/linkable")
                .param("productIds",inner.toString()).param("direction","WAREHOUSE_TO_PRODUCTION"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(0));
    }

    @Test void customNameRoundTripsThroughTaskOptionsAndLinkedMovementWithoutMaterialConcatenation(){
        var base=request(target(inner,500),target(outer,500));
        var named=new ProductionTaskService.SaveRequest(null,null,base.plannedDate(),base.batchNo(),null,base.targets(),drink,500L,"  轻体粉第一批  ");
        var task=tasks.create(named);
        assertThat(task.name()).isEqualTo("轻体粉第一批");
        assertThat(tasks.detail(task.id()).name()).isEqualTo(task.name());
        assertThat(tasks.linkable(List.of(inner),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,false))
                .filteredOn(t->t.id().equals(task.id())).extracting(ProductionTaskService.LinkOption::name).containsExactly(task.name());
        var movement=move(task.id(),inner,200L,false,false);
        assertThat(movement.productionTaskName()).isEqualTo(task.name());
        var changed=new ProductionTaskService.SaveRequest(null,null,base.plannedDate(),base.batchNo(),null,base.targets(),drink,500L,"轻体粉第二批");
        tasks.update(task.id(),changed);
        assertThat(movements.detail(movement.id()).productionTaskName()).isEqualTo("轻体粉第二批");
        // Older clients omit name: editing other fields must not erase it.
        assertThat(tasks.update(task.id(),base).name()).isEqualTo("轻体粉第二批");
        var detail=tasks.detail(task.id());
        assertThat(detail.targets().getFirst().shortageQuantity()).isEqualTo(300);
        assertThat(detail.progressPercent()).isEqualTo(20);
    }

    @Test void unnamedLegacyTasksHaveShortFallbackAndInvalidNamesAreRejected(){
        var base=request(target(inner,500),target(outer,500));
        var legacy=tasks.create(base);
        assertThat(legacy.name()).doesNotContain("、").endsWith(" · "+base.batchNo());
        for(String invalid:List.of("   ","x".repeat(101))) {
            var bad=new ProductionTaskService.SaveRequest(null,null,base.plannedDate(),base.batchNo(),null,base.targets(),drink,500L,invalid);
            assertThatThrownBy(()->tasks.create(bad)).hasMessageContaining("任务名称");
        }
    }

    private Long product(String name,ProductUsage usage){
        String suffix=UUID.randomUUID().toString();
        return products.create(new ProductDtos.Request(name+suffix,suffix,null,usage,10,"个")).id();
    }
    private ProductionTaskService.TargetRequest target(Long id,long quantity){return new ProductionTaskService.TargetRequest(id,quantity);}
    private ProductionTaskService.SaveRequest request(ProductionTaskService.TargetRequest... targets){
        return new ProductionTaskService.SaveRequest(null,null,LocalDate.of(2026,10,8),"MATERIAL-PLAN","生产 12000 个关节饮",List.of(targets),drink,12000L);
    }
    private MovementDtos.DetailResponse move(Long taskId,Long productId,Long quantity,boolean returned,boolean unknown){
        var direction=returned||productId.equals(drink)?MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE:MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION;
        var item=new MovementDtos.ItemRequest(productId,"LOT",0,0,quantity,unknown,null,List.of());
        return movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),direction,Instant.parse("2026-10-08T00:00:00Z"),
                person,person,"LOT",returned,taskId,null,List.of(item)));
    }
}
