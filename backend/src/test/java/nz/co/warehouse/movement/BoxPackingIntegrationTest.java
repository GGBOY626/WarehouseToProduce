package nz.co.warehouse.movement;

import nz.co.warehouse.product.*;
import nz.co.warehouse.person.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:warehouse;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Sql(statements="CREATE TABLE IF NOT EXISTS movement_daily_sequence (sequence_date DATE PRIMARY KEY, next_value INT NOT NULL)")
class BoxPackingIntegrationTest {
    @Autowired ProductService products;
    @Autowired MovementService movements;
    @Autowired PersonService persons;
    Long person;
    @BeforeEach void setup(){person=persons.create(new PersonDtos.Request("Box-"+UUID.randomUUID(),null)).id();}
    ProductDtos.Request productRequest(String name,BoxPacking... packings){
        return new ProductDtos.Request(name,name,null,ProductUsage.PRODUCTION_TO_WAREHOUSE,null,"条",false,List.of(),List.of(),List.of(packings));
    }
    ProductDtos.Response product(){return products.create(productRequest("Box-"+UUID.randomUUID(),new BoxPacking(28,30)));}
    MovementDtos.ItemRequest item(ProductDtos.Response p,Long id,Integer boxes,Integer units,Integer tailBoxes,Integer totalPerCarton,Long tailUnits){
        return new MovementDtos.ItemRequest(p.id(),p.materialCode(),2,0,null,false,null,List.of(),totalPerCarton,1,tailUnits,id,null,null,boxes,units,tailBoxes);
    }
    MovementDtos.SaveRequest request(MovementDtos.ItemRequest item){return new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE,Instant.now(),person,person,null,false,null,null,List.of(item));}
    @Test void productFactorsRoundTripAndServerCalculatesFullAndTailQuantities(){
        var p=product();
        assertThat(products.search(p.name(),true,p.movementDirection()).getFirst().boxPackings()).containsExactly(new BoxPacking(28,30));
        var saved=movements.create(request(item(p,null,28,30,12,null,null)));
        var i=saved.items().getFirst();
        assertThat(i.unitsPerCarton()).isEqualTo(840);assertThat(i.tailUnitsPerCarton()).isEqualTo(360);
        assertThat(i.totalUnits()).isEqualTo(2040);assertThat(saved.totalCartons()).isEqualTo(3);
        assertThat(i.boxesPerCarton()).isEqualTo(28);assertThat(i.unitsPerBox()).isEqualTo(30);
        assertThat(saved.materials().getFirst().totalQuantity()).isEqualTo(2040);
        products.update(p.id(),productRequest(p.name(),new BoxPacking(24,20)));
        var edited=movements.update(saved.id(),request(item(p,i.id(),28,30,12,840,360L)));
        assertThat(edited.items().getFirst().totalUnits()).isEqualTo(2040);
        assertThat(edited.items().getFirst().unitsPerBox()).isEqualTo(30);
        var fromOldClient=movements.update(saved.id(),request(item(p,edited.items().getFirst().id(),null,null,null,840,360L)));
        assertThat(fromOldClient.items().getFirst().unitsPerBox()).isEqualTo(30);
        assertThat(fromOldClient.items().getFirst().totalUnits()).isEqualTo(2040);
    }
    @Test void legacyRecordsKeepTheirTotalsAndDoNotInventFactors(){
        var p=product();var saved=movements.create(request(item(p,null,null,null,null,840,360L)));
        var old=saved.items().getFirst();assertThat(old.boxesPerCarton()).isNull();assertThat(old.totalUnits()).isEqualTo(2040);
        var edited=movements.update(saved.id(),request(item(p,old.id(),null,null,null,null,360L)));
        assertThat(edited.items().getFirst().unitsPerCarton()).isEqualTo(840);assertThat(edited.items().getFirst().totalUnits()).isEqualTo(2040);
    }
    @Test void incompleteFactorsMismatchedTotalsAndOverflowAreRejected(){
        var p=product();
        assertThatThrownBy(()->movements.create(request(item(p,null,28,null,12,null,null)))).hasMessageContaining("每箱盒数");
        assertThatThrownBy(()->movements.create(request(item(p,null,28,30,12,900,null)))).hasMessageContaining("不一致");
        assertThatThrownBy(()->movements.create(request(item(p,null,28,30,12,840,12L)))).hasMessageContaining("不一致");
        assertThatThrownBy(()->movements.create(request(item(p,null,28,30,null,null,null)))).hasMessageContaining("实际盒数");
        assertThatThrownBy(()->movements.create(request(item(p,null,Integer.MAX_VALUE,30,12,null,null)))).hasMessageContaining("超出");
        assertThatThrownBy(()->products.create(productRequest("Bad-"+UUID.randomUUID(),new BoxPacking(28,null)))).hasMessageContaining("每箱盒数");
    }
    @Test void differentPackingsWithEqualTotalsRemainSeparatePresets(){
        var p=products.create(productRequest("Mixed-"+UUID.randomUUID(),new BoxPacking(28,30),new BoxPacking(14,60)));
        assertThat(p.boxPackings()).hasSize(2);
        assertThat(movements.create(request(item(p,null,14,60,12,null,null))).items().getFirst().tailUnitsPerCarton()).isEqualTo(720);
    }
}
