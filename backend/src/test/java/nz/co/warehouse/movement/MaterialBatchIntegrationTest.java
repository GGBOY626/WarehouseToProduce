package nz.co.warehouse.movement;

import nz.co.warehouse.product.*;
import nz.co.warehouse.person.*;
import nz.co.warehouse.production.*;
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
class MaterialBatchIntegrationTest {
    @Autowired ProductService products;
    @Autowired MovementService movements;
    @Autowired ProductionTaskService tasks;
    @Autowired PersonService persons;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    ProductDtos.Response material,output;
    Long person;
    String code;
    final LocalDate day=LocalDate.of(2026,10,12);
    @BeforeEach void setup(){
        code="BATCH-"+UUID.randomUUID();
        material=products.create(new ProductDtos.Request(code,code,null,ProductUsage.WAREHOUSE_TO_PRODUCTION,1000,"个",false,List.of(1000,1800),List.of("A001","A002")));
        output=products.create(new ProductDtos.Request(code,code,null,ProductUsage.PRODUCTION_TO_WAREHOUSE,1000,"个",false,List.of(),List.of("F001","F002")));
        person=persons.create(new PersonDtos.Request(code,null)).id();
    }
    MovementDtos.ItemRequest item(Long id,String batch,long loose,Long rowId){
        return new MovementDtos.ItemRequest(id,code,0,loose,null,false,null,List.of(),1000,0,null,rowId,"outer-1",batch);
    }
    MovementDtos.SaveRequest request(MovementEnums.Direction direction,Long task,MovementDtos.ItemRequest... items){
        return new MovementDtos.SaveRequest(UUID.randomUUID().toString(),direction,Instant.parse("2026-10-12T02:00:00Z"),person,person,null,false,task,null,List.of(items));
    }
    ProductionTaskService.SaveRequest task(String outputBatch,ProductionTaskService.TargetRequest... targets){
        return new ProductionTaskService.SaveRequest(null,null,day,"TASK-BATCH",null,List.of(targets),output.id(),100L,"Batch task",outputBatch);
    }
    @Test void mixedBatchesAreStoredEditedSearchedAndGroupedIndependently(){
        var saved=movements.create(request(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,null,item(material.id(),"A001",15,null),item(material.id(),"A002",3,null)));
        assertThat(saved.manufactureLot()).isNull();
        assertThat(saved.materials()).extracting(MovementDtos.ListItemResponse::materialBatch).containsExactly("A001","A002");
        assertThat(saved.materials()).extracting(MovementDtos.ListItemResponse::totalQuantity).containsExactly(15L,3L);
        var stats=movements.queryStats(day,day,null,MovementEnums.Status.ACTIVE,null,null,code).directions().getFirst();
        assertThat(stats.products()).hasSize(2);
        assertThat(movements.search(day,day,null,null,null,null,"A002",0,100).content()).extracting(MovementDtos.ListResponse::id).contains(saved.id());
        assertThat(movements.queryStats(day,day,null,null,null,null,"A002").directions()).isNotEmpty();
        var changed=movements.update(saved.id(),request(saved.direction(),null,item(material.id(),"A003",15,saved.items().getFirst().id()),item(material.id(),"A002",3,saved.items().get(1).id())));
        assertThat(changed.materials()).extracting(MovementDtos.ListItemResponse::materialBatch).containsExactly("A003","A002");
        assertThat(products.search(code,false,ProductUsage.WAREHOUSE_TO_PRODUCTION)).singleElement().satisfies(p->assertThat(p.materialBatches()).containsExactly("A001","A002","A003"));
        assertThat(products.search("A003",false,ProductUsage.WAREHOUSE_TO_PRODUCTION)).extracting(ProductDtos.Response::id).contains(material.id());
    }
    @Test void multipleBatchesRequireChoiceOnMovementsButNotTaskRequirements(){
        assertThatThrownBy(()->movements.create(request(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,null,item(material.id(),"",3,null)))).hasMessageContaining("物料批次");
        assertThatThrownBy(()->movements.create(request(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,null,item(output.id(),"",3,null)))).hasMessageContaining("物料批次");
        assertThatCode(()->tasks.create(task("F001",new ProductionTaskService.TargetRequest(material.id(),20)))).doesNotThrowAnyException();
        assertThatCode(()->tasks.create(task(null,new ProductionTaskService.TargetRequest(material.id(),20,"A001")))).doesNotThrowAnyException();
    }
    @Test void taskProgressCombinesBatchesAndRetainsBatchBreakdown(){
        var saved=tasks.create(task("F001",new ProductionTaskService.TargetRequest(material.id(),20,"A001"),new ProductionTaskService.TargetRequest(material.id(),30,"A002")));
        movements.create(request(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,saved.id(),item(material.id(),"A001",10,null),item(material.id(),"A002",3,null)));
        var receipt=movements.create(request(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,saved.id(),item(output.id(),"F001",5,null)));
        assertThat(tasks.detail(saved.id()).targets()).extracting(ProductionTaskService.TargetResponse::completedQuantity).containsExactly(13L);
        assertThat(tasks.detail(saved.id()).output().receivedQuantity()).isEqualTo(5);
        assertThat(tasks.linkableBatches(new ProductionTaskService.LinkRequest(List.of(new ProductionTaskService.BatchSelection(output.id(),"F002")),receipt.direction(),false))).extracting(ProductionTaskService.LinkOption::id).contains(saved.id());
        assertThatCode(()->movements.update(receipt.id(),request(receipt.direction(),saved.id(),item(output.id(),"F002",5,receipt.items().getFirst().id())))).doesNotThrowAnyException();
        movements.create(request(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,saved.id(),item(material.id(),"A003",4,null)));
        tasks.update(saved.id(),task("F001",new ProductionTaskService.TargetRequest(material.id(),30,"A002"),new ProductionTaskService.TargetRequest(material.id(),20,"A001")));
        assertThat(tasks.detail(saved.id()).targets()).extracting(ProductionTaskService.TargetResponse::completedQuantity).containsExactly(17L);
        assertThat(tasks.detail(saved.id()).targets().getFirst().batches()).extracting(ProductionTaskService.BatchResponse::netQuantity).containsExactly(10L,3L,4L);
    }
    @Test void mergedAliasKeepsHistoryAndResolvesToOneSelectableProduct(){
        var donor=products.create(new ProductDtos.Request(code+"-old",code,"A002",ProductUsage.WAREHOUSE_TO_PRODUCTION,1800,"个"));
        var old=movements.create(request(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,null,item(donor.id(),"A002",7,null)));
        jdbc.update("update product set merged_into_id=?,active=false where id=?",material.id(),donor.id());
        assertThat(products.search(code,true,ProductUsage.WAREHOUSE_TO_PRODUCTION)).extracting(ProductDtos.Response::id).containsExactly(material.id());
        var detail=movements.detail(old.id());
        assertThat(detail.items().getFirst().productId()).isEqualTo(material.id());
        assertThat(detail.items().getFirst().productName()).isEqualTo(code+"-old");
        assertThat(detail.items().getFirst().totalUnits()).isEqualTo(7);
        var edited=movements.update(old.id(),request(old.direction(),null,item(material.id(),"A002",8,detail.items().getFirst().id())));
        assertThat(edited.items().getFirst().productName()).isEqualTo(code+"-old");
        assertThat(edited.items().getFirst().materialBatch()).isEqualTo("A002");
        assertThatThrownBy(()->products.delete(material.id())).hasMessageContaining("不能删除");
    }
    @Test void legacyHeaderBatchAndLooseQuantitiesStayReadable(){
        var saved=movements.create(request(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,null,item(material.id(),"A001",3,null)));
        jdbc.update("update movement_item set material_batch=null where movement_id=?",saved.id());
        var old=movements.detail(saved.id());
        assertThat(old.items().getFirst().materialBatch()).isEqualTo("A001");
        assertThat(old.items().getFirst().looseUnits()).isEqualTo(3);
        assertThat(old.totalCartons()).isZero();
        assertThat(old.items().getFirst().tailCartons()).isZero();
    }
    @Test void productionNumbersRequireChoiceWhenMultipleAndRemainOnHistoricalItems(){
        var numbered=products.create(new ProductDtos.Request(code+"-numbered",code+"-N",null,ProductUsage.WAREHOUSE_TO_PRODUCTION,1000,"个",false,List.of(),List.of("A001"),List.of(),List.of("P001","P002")));
        var missing=new MovementDtos.ItemRequest(numbered.id(),code+"-N",0,3,null,false,null,List.of(),1000,0,null,null,null,"A001",null,null,null,null);
        assertThatThrownBy(()->movements.create(request(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,null,missing))).hasMessageContaining("生产编号");
        var selected=new MovementDtos.ItemRequest(numbered.id(),code+"-N",0,3,null,false,null,List.of(),1000,0,null,null,null,"A001",null,null,null,"P002");
        var saved=movements.create(request(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,null,selected));
        assertThat(saved.items().getFirst().productionNumber()).isEqualTo("P002");
        assertThat(products.search("P002",false,ProductUsage.WAREHOUSE_TO_PRODUCTION)).extracting(ProductDtos.Response::id).contains(numbered.id());
        var edited=new MovementDtos.ItemRequest(numbered.id(),code+"-N",0,4,null,false,null,List.of(),1000,0,null,saved.items().getFirst().id(),null,"A001",null,null,null,null);
        assertThat(movements.update(saved.id(),request(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,null,edited)).items().getFirst().productionNumber()).isEqualTo("P002");
    }
    ProductionTaskService.SaveRequest selectedTask(List<String> batches,List<String> codes,List<String> numbers){
        var payload=new LinkedHashMap<String,Object>();
        payload.put("plannedDate",day.toString());
        payload.put("targets",List.of(new ProductionTaskService.TargetRequest(material.id(),20,null,null,null,List.of("A002"),List.of(code),List.of())));
        payload.put("outputProductId",output.id());payload.put("targetOutputQuantity",100);
        payload.put("outputMaterialBatches",batches);payload.put("outputMaterialCodes",codes);payload.put("outputProductionNumbers",numbers);
        return mapper.convertValue(payload,ProductionTaskService.SaveRequest.class);
    }
    @Test void taskSelectionsSurviveSavingAndLinkOptionsForBothDirections(){
        products.update(output.id(),new ProductDtos.Request(code,code,null,null,ProductUsage.PRODUCTION_TO_WAREHOUSE,1000,"个",false,List.of(),List.of("F001","F002"),List.of(),List.of("P001","P002"),List.of(code,code+"-2")));
        var saved=tasks.create(selectedTask(List.of("F002"),List.of(code+"-2"),List.of("P002")));
        var detail=tasks.detail(saved.id());
        assertThat(detail.output().materialBatches()).containsExactly("F002");
        assertThat(detail.output().materialCodes()).containsExactly(code+"-2");
        assertThat(detail.output().productionNumbers()).containsExactly("P002");
        var issueOptions=tasks.linkableBatches(new ProductionTaskService.LinkRequest(List.of(new ProductionTaskService.BatchSelection(material.id(),null)),MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION,false));
        assertThat(issueOptions).filteredOn(option->option.id().equals(saved.id())).singleElement().satisfies(option->{
            assertThat(option.targets().getFirst().materialBatches()).containsExactly("A002");
            assertThat(option.targets().getFirst().materialCodes()).containsExactly(code);
        });
        var receiptOptions=tasks.linkableBatches(new ProductionTaskService.LinkRequest(List.of(new ProductionTaskService.BatchSelection(output.id(),null)),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,false));
        assertThat(receiptOptions).filteredOn(option->option.id().equals(saved.id())).singleElement().satisfies(option->{
            assertThat(option.output().productionNumbers()).containsExactly("P002");
            assertThat(option.output().materialCodes()).containsExactly(code+"-2");
        });
        var selected=new MovementDtos.ItemRequest(output.id(),code+"-2",0,3,null,false,null,List.of(),1000,0,null,null,null,"F002",null,null,null,"P002");
        var receipt=movements.create(request(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,saved.id(),selected));
        assertThat(receipt.items().getFirst().productionNumber()).isEqualTo("P002");
        assertThat(receipt.items().getFirst().materialBatch()).isEqualTo("F002");
        assertThat(receipt.items().getFirst().batchNo()).isEqualTo(code+"-2");
        var wrongBatch=new MovementDtos.ItemRequest(output.id(),code+"-2",0,3,null,false,null,List.of(),1000,0,null,null,null,"F001",null,null,null,"P002");
        assertThatThrownBy(()->movements.create(request(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,saved.id(),wrongBatch))).hasMessageContaining("不匹配");
        tasks.update(saved.id(),selectedTask(List.of("F001","F002"),List.of(code),List.of("P001","P002")));
        assertThat(tasks.detail(saved.id()).output().productionNumbers()).containsExactly("P001","P002");
        tasks.update(saved.id(),selectedTask(List.of(),List.of(),List.of()));
        assertThat(tasks.detail(saved.id()).output().productionNumbers()).isEmpty();
    }
    @Test void outputSelectionsMustBelongToSelectedProduct(){
        assertThatThrownBy(()->tasks.create(selectedTask(List.of("UNKNOWN"),List.of(),List.of()))).hasMessageContaining("不属于");
        assertThatThrownBy(()->tasks.create(selectedTask(List.of(),List.of("UNKNOWN"),List.of()))).hasMessageContaining("不属于");
        assertThatThrownBy(()->tasks.create(selectedTask(List.of(),List.of(),List.of("UNKNOWN")))).hasMessageContaining("不属于");
    }

}
