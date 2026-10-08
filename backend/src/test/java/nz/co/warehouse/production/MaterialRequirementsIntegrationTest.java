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
@Sql(statements="CREATE TABLE IF NOT EXISTS movement_daily_sequence (sequence_date DATE PRIMARY KEY, next_value INT NOT NULL)")
class MaterialRequirementsIntegrationTest {
    @Autowired ProductionTaskService tasks;
    @Autowired MovementService movements;
    @Autowired ProductService products;
    @Autowired PersonService persons;
    @Autowired JdbcTemplate jdbc;
    Long inner,outer,drink,person;

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
        movements.voidMovement(voided.id(),"duplicate");
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
        movements.voidMovement(returned.id(),"incorrect return");
        assertThat(tasks.detail(task.id()).targets().getFirst().shortageQuantity()).isZero();
    }

    private Long product(String name,ProductUsage usage){
        String suffix=UUID.randomUUID().toString();
        return products.create(new ProductDtos.Request(name+suffix,suffix,null,usage,10,"个")).id();
    }
    private ProductionTaskService.TargetRequest target(Long id,long quantity){return new ProductionTaskService.TargetRequest(id,quantity);}
    private ProductionTaskService.SaveRequest request(ProductionTaskService.TargetRequest... targets){
        return new ProductionTaskService.SaveRequest(null,null,LocalDate.of(2026,10,8),"MATERIAL-PLAN","生产 12000 个关节饮",List.of(targets));
    }
    private MovementDtos.DetailResponse move(Long taskId,Long productId,Long quantity,boolean returned,boolean unknown){
        var direction=returned||productId.equals(drink)?MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE:MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION;
        var item=new MovementDtos.ItemRequest(productId,"LOT",0,0,quantity,unknown,null,List.of());
        return movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),direction,Instant.parse("2026-10-08T00:00:00Z"),
                person,person,"LOT",returned,taskId,null,List.of(item)));
    }
}
