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
    "spring.datasource.url=jdbc:h2:mem:forecast;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"
})
@Sql(statements="CREATE TABLE IF NOT EXISTS movement_daily_sequence (sequence_date DATE PRIMARY KEY, next_value INT NOT NULL)")
class FinishedReceiptForecastTest {
    @Autowired ProductionTaskService tasks;
    @Autowired MovementService movements;
    @Autowired ProductService products;
    @Autowired PersonService persons;
    @Autowired JdbcTemplate jdbc;
    Long raw, output, person;
    String name;
    @BeforeEach void setup(){
        name="Forecast-"+UUID.randomUUID();
        raw=products.create(new ProductDtos.Request(name,"RAW",null,ProductUsage.WAREHOUSE_TO_PRODUCTION,400,"个")).id();
        output=products.create(new ProductDtos.Request(name,"FIN",null,ProductUsage.PRODUCTION_TO_WAREHOUSE,null,"个",false,List.of(),List.of(),List.of(new BoxPacking(22,30,40)))).id();
        person=persons.create(new PersonDtos.Request(name,null)).id();
    }
    ProductionTaskService.SaveRequest plan(Long input,Long yield,Integer pallet){
        return new ProductionTaskService.SaveRequest(null,null,LocalDate.now(),"LOT",null,
                List.of(new ProductionTaskService.TargetRequest(raw,132000)),output,null,"Forecast",null,
                22,30,ProductionTask.OutputQuantityBasis.UNIT,raw,pallet,input,yield);
    }
    MovementDtos.DetailResponse move(Long task,Long product,long quantity,boolean returned){
        var direction=returned||product.equals(output)?MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE:MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION;
        return movements.create(new MovementDtos.SaveRequest(UUID.randomUUID().toString(),direction,Instant.now(),person,person,null,returned,task,null,
                List.of(new MovementDtos.ItemRequest(product,"CODE",0,quantity,null,false,null,List.of(),null,0,null,null,null,"A"))));
    }
    @Test void explicitRatioAndMultipleReceiptsUseFinishedPackingRatherThanRawPacking(){
        var task=tasks.create(plan(2L,1L,40));
        move(task.id(),raw,132000,false); // 2 raw units -> 1 finished unit -> 100 cartons of 660
        move(task.id(),output,40*660,false);move(task.id(),output,44*660,false);
        var f=tasks.detail(task.id()).forecast();
        assertThat(f.expected().cartons()).isEqualTo("100");assertThat(f.expected().pallets()).isEqualTo("2");
        assertThat(f.expected().palletCartons()).isEqualTo("20");
        assertThat(f.received().cartons()).isEqualTo("84");assertThat(f.remaining().cartons()).isEqualTo("16");
        assertThat(f.progressPercent()).isEqualByComparingTo("84");assertThat(f.excess()).isNull();
        assertThat(tasks.list(null).stream().filter(t->t.id().equals(task.id())).findFirst().orElseThrow().forecast()).isEqualTo(f);
        // Product preset edits do not rewrite the task snapshot.
        products.update(output,new ProductDtos.Request(name,"FIN",null,ProductUsage.PRODUCTION_TO_WAREHOUSE,null,"个",false,List.of(),List.of(),List.of(new BoxPacking(10,10,10))));
        assertThat(tasks.detail(task.id()).forecast()).isEqualTo(f);
    }
    @Test void unrelatedSameNameReturnsVoidsAndDeletesCannotDoubleCount(){
        var task=tasks.create(plan(2L,1L,null));
        move(task.id(),raw,132000,false);move(task.id(),raw,1320,true);
        move(null,output,66000,false);
        var receipt=move(task.id(),output,660,false);
        var voided=move(task.id(),output,660,false);
        jdbc.update("UPDATE movement SET status='VOID' WHERE id=?",voided.id());
        var f=tasks.detail(task.id()).forecast();
        assertThat(f.expected().cartons()).isEqualTo("99");assertThat(f.received().cartons()).isEqualTo("1");
        assertThat(f.remaining().cartons()).isEqualTo("98");assertThat(f.expected().pallets()).isNull();
        movements.delete(receipt.id());
        assertThat(tasks.detail(task.id()).forecast().received().cartons()).isEqualTo("0");
        assertThat(tasks.detail(task.id()).forecast().remaining().cartons()).isEqualTo("99");
    }
    @Test void missingRatioIsNotGuessedAndNoIssuesOrReceiptsAreZero(){
        var legacy=tasks.create(plan(null,null,null));
        move(legacy.id(),raw,66000,false);
        assertThat(tasks.detail(legacy.id()).forecast().status()).isEqualTo("NEEDS_CONFIGURATION");
        assertThat(tasks.detail(legacy.id()).forecast().expected()).isNull();
        var configured=tasks.create(plan(2L,1L,null));
        assertThat(configured.forecast().expected().cartons()).isEqualTo("0");
        assertThat(configured.forecast().received().cartons()).isEqualTo("0");
        assertThat(configured.forecast().progressPercent()).isNull();
        move(configured.id(),raw,1320,false);
        assertThat(tasks.detail(configured.id()).forecast().remaining().cartons()).isEqualTo("1");
        assertThat(tasks.detail(configured.id()).forecast().progressPercent()).isZero();
        var noIssue=tasks.create(plan(2L,1L,null));move(noIssue.id(),output,660,false);
        assertThat(tasks.detail(noIssue.id()).forecast().excess().cartons()).isEqualTo("1");
        assertThat(tasks.detail(noIssue.id()).forecast().progressPercent()).isNull();
    }
    @Test void overReceiptsAndFractionalPackingAreNeverRoundedAway(){
        var task=tasks.create(plan(3L,2L,2));
        move(task.id(),raw,2000,false); // 1333 + 1/3 units = 2 cartons, 13 + 1/3 units
        var f=tasks.detail(task.id()).forecast();
        assertThat(f.expected().cartons()).isEqualTo("2");assertThat(f.expected().units()).isEqualTo("13");
        assertThat(f.expected().fraction()).isEqualTo("1/3");assertThat(f.expected().pallets()).isEqualTo("1");
        move(task.id(),output,1400,false);
        f=tasks.detail(task.id()).forecast();
        assertThat(f.received().boxes()).isEqualTo("2");assertThat(f.received().units()).isEqualTo("20");
        assertThat(f.remaining().cartons()).isEqualTo("0");assertThat(f.excess().boxes()).isEqualTo("2");
        assertThat(f.excess().units()).isEqualTo("6");assertThat(f.excess().fraction()).isEqualTo("2/3");
        assertThat(f.progressPercent()).isEqualByComparingTo("105");
    }
    @Test void unknownQuantitiesAndNegativeNetIssuesDoNotProduceFalseProgress(){
        var task=tasks.create(plan(1L,1L,null));
        var issue=move(task.id(),raw,1320,false);var receipt=move(task.id(),output,660,false);
        jdbc.update("UPDATE movement_item SET quantity_unknown=true,total_units=null WHERE movement_id=?",receipt.id());
        var f=tasks.detail(task.id()).forecast();
        assertThat(f.receivedIncomplete()).isTrue();assertThat(f.remaining()).isNull();assertThat(f.progressPercent()).isNull();
        jdbc.update("UPDATE movement_item SET quantity_unknown=true,total_units=null WHERE movement_id=?",issue.id());
        assertThat(tasks.detail(task.id()).forecast().expected()).isNull();
        jdbc.update("UPDATE movement_item SET quantity_unknown=false,total_units=1 WHERE movement_id=?",issue.id());
        move(task.id(),raw,2,true);
        assertThat(tasks.detail(task.id()).forecast().expected()).isNull();
    }
    @Test void boxBasedReceiptsUseBoxesPerCartonNotContentsPerBox(){
        var request=new ProductionTaskService.SaveRequest(null,null,LocalDate.now(),"BOX",null,
                List.of(new ProductionTaskService.TargetRequest(raw,66000)),output,null,"Box forecast",null,
                22,30,ProductionTask.OutputQuantityBasis.BOX,raw,40,30L,1L);
        var task=tasks.create(request);move(task.id(),raw,66000,false);move(task.id(),output,1848,false);
        assertThat(tasks.detail(task.id()).forecast().expected().cartons()).isEqualTo("100");
        assertThat(tasks.detail(task.id()).forecast().remaining().cartons()).isEqualTo("16");
    }
    @Test void productPalletAndRatioValidationAndOldProgressRemainIndependent(){
        assertThatThrownBy(()->products.update(output,new ProductDtos.Request(name,"FIN",null,ProductUsage.PRODUCTION_TO_WAREHOUSE,null,"个",false,List.of(),List.of(),List.of(new BoxPacking(22,30,0)))))
                .hasMessageContaining("托盘");
        assertThatThrownBy(()->products.update(output,new ProductDtos.Request(name,"FIN",null,ProductUsage.PRODUCTION_TO_WAREHOUSE,null,"个",false,List.of(),List.of(),List.of(new BoxPacking(22,30,40),new BoxPacking(22,30,50)))))
                .hasMessageContaining("同一箱规");
        assertThat(products.search("FIN",false,ProductUsage.PRODUCTION_TO_WAREHOUSE)).filteredOn(p->p.id().equals(output)).singleElement()
                .satisfies(p->assertThat(p.boxPackings().getFirst().getCartonsPerPallet()).isEqualTo(40));
        assertThatThrownBy(()->tasks.create(plan(0L,1L,null))).hasMessageContaining("配比");
        assertThatThrownBy(()->tasks.create(plan(1L,null,null))).hasMessageContaining("配比");
        assertThatThrownBy(()->tasks.create(plan(1L,1L,0))).hasMessageContaining("托盘");
        var task=tasks.create(plan(null,null,null));move(task.id(),raw,1320,false);move(task.id(),output,660,false);
        var original=tasks.detail(task.id());var changed=tasks.update(task.id(),plan(2L,1L,40));
        assertThat(changed.progressPercent()).isEqualTo(original.progressPercent());
        assertThat(changed.output().productionPercent()).isEqualByComparingTo(original.output().productionPercent());
        assertThat(changed.forecast().progressPercent()).isEqualByComparingTo("100");
    }
}
