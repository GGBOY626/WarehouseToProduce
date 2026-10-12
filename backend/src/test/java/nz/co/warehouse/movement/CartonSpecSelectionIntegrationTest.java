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

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:warehouse;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"
})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Sql(statements="CREATE TABLE IF NOT EXISTS movement_daily_sequence (sequence_date DATE PRIMARY KEY, next_value INT NOT NULL)")
class CartonSpecSelectionIntegrationTest {
    @Autowired ProductService products;
    @Autowired MovementService movements;
    @Autowired PersonService persons;
    Long person;
    @BeforeEach void setup(){person=persons.create(new PersonDtos.Request("Specs-"+UUID.randomUUID(),null)).id();}
    ProductDtos.Response product(ProductUsage direction,Integer legacy,List<Integer> specs){
        String name="Specs-"+UUID.randomUUID();
        return products.create(new ProductDtos.Request(name,name,"A001",direction,legacy,"个",false,specs));
    }
    MovementDtos.ItemRequest item(ProductDtos.Response p,Integer units,int full,int tails,Long tailUnits,long loose){
        return new MovementDtos.ItemRequest(p.id(),p.materialCode(),full,loose,null,false,null,List.of(),units,tails,tailUnits,null,null,"A001");
    }
    MovementDtos.SaveRequest request(ProductDtos.Response p,MovementDtos.ItemRequest... items){
        return new MovementDtos.SaveRequest(UUID.randomUUID().toString(),MovementEnums.Direction.valueOf(p.movementDirection().name()),Instant.parse("2026-10-12T03:00:00Z"),person,person,null,false,null,null,List.of(items));
    }
    @Test void bothDirectionsRequireSelectionDespiteAnExistingLegacyDefault(){
        for(ProductUsage direction:ProductUsage.values()){
            var p=product(direction,500,List.of(1000,1800));
            assertThatThrownBy(()->movements.create(request(p,item(p,null,2,0,null,0)))).hasMessageContaining("选择本次完整箱规格");
            var selected=movements.create(request(p,item(p,1800,2,0,null,0)));
            assertThat(selected.items().getFirst().unitsPerCarton()).isEqualTo(1800);
            assertThat(selected.items().getFirst().totalUnits()).isEqualTo(3600);
        }
    }
    @Test void onePresetWithoutLegacyDefaultCanStillBeAutomaticallyUsed(){
        var p=product(ProductUsage.WAREHOUSE_TO_PRODUCTION,null,List.of(500));
        var saved=movements.create(request(p,item(p,null,2,0,null,0)));
        assertThat(saved.items().getFirst().unitsPerCarton()).isEqualTo(500);
        assertThat(saved.items().getFirst().totalUnits()).isEqualTo(1000);
    }
    @Test void presetsRoundTripAndMixedOrTemporarySpecificationsKeepSeparateBoxCounts(){
        var p=product(ProductUsage.WAREHOUSE_TO_PRODUCTION,null,List.of(500,1000,1800));
        assertThat(products.search(p.name(),false,p.movementDirection()).getFirst().cartonSpecs()).containsExactly(500,1000,1800);
        var saved=movements.create(request(p,item(p,500,2,0,null,0),item(p,1800,3,0,null,0),item(p,2000,1,0,null,0)));
        assertThat(saved.materials().getFirst().packaging()).containsExactly(
            new MaterialQuantity.Packaging("FULL",500L,2),new MaterialQuantity.Packaging("FULL",1800L,3),new MaterialQuantity.Packaging("FULL",2000L,1));
        assertThat(saved.materials().getFirst().totalQuantity()).isEqualTo(8400);
        assertThat(saved.totalCartons()).isEqualTo(6);
        var old=saved.items().getFirst();
        var edited=new MovementDtos.ItemRequest(p.id(),p.materialCode(),2,0,null,false,"note",List.of(),null,0,null,old.id(),null,"A001");
        assertThat(movements.update(saved.id(),request(p,edited)).items().getFirst().unitsPerCarton()).isEqualTo(500);
        assertThatThrownBy(()->movements.update(saved.id(),request(p,item(p,null,1,0,null,0)))).hasMessageContaining("选择本次完整箱规格");
    }
    @Test void tailAndLooseOnlyDoNotRequireAnUnrelatedFullCartonSpecification(){
        var p=product(ProductUsage.WAREHOUSE_TO_PRODUCTION,null,List.of(500,1000,1800));
        var saved=movements.create(request(p,item(p,null,0,1,3L,2)));
        assertThat(saved.items().getFirst().unitsPerCarton()).isNull();
        assertThat(saved.totalCartons()).isEqualTo(1);
        assertThat(saved.items().getFirst().totalUnits()).isEqualTo(5);
    }
}
