package nz.co.warehouse.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import nz.co.warehouse.auth.AuthController;
import nz.co.warehouse.product.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:warehouse_inventory;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"})
@AutoConfigureMockMvc
class WarehouseIntegrationTest {
    @Autowired WarehouseService service;
    @Autowired WarehouseAreaRepository areas;
    @Autowired ProductService products;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    Long productId,secondProductId;
    WarehouseArea rack;
    @BeforeEach void setup(){
        productId=product("个");secondProductId=product("条");
        rack=new WarehouseArea();rack.setName("Rack-"+UUID.randomUUID());rack.setKind(WarehouseArea.Kind.RACK);rack.setLanes(3);rack=areas.saveAndFlush(rack);
    }
    Long product(String unit){String name="stock-"+UUID.randomUUID();return products.create(new ProductDtos.Request(name,name,null,ProductUsage.WAREHOUSE_TO_PRODUCTION,null,unit,false,List.of(),List.of())).id();}
    WarehouseService.SaveRequest request(Long area,String lane,int position,Long version){return new WarehouseService.SaveRequest("TP-"+UUID.randomUUID(),area,lane,position,"现场记录",List.of(new WarehouseService.ItemRequest(productId,"22334",2000L,2),new WarehouseService.ItemRequest(secondProductId,"22335",null,3)),version);}
    MockHttpSession session(){var s=new MockHttpSession();s.setAttribute(AuthController.AUTH_SESSION_KEY,true);return s;}

    @Test void mixedPalletMoveAndRemovalRetainHistory(){
        var p=service.create(request(rack.getId(),"UPPER",1,null));
        assertThat(p.items()).hasSize(2);assertThat(p.items().get(1).quantity()).isNull();
        var aisle=service.createAisle(new WarehouseService.AreaRequest("Aisle-"+UUID.randomUUID(),4,null));
        var updated=service.update(p.id(),request(aisle.id(),"RIGHT_2",3,p.version()));
        assertThat(updated.location()).contains("右二","03");assertThat(updated.version()).isGreaterThan(p.version());
        assertThat(service.history(p.id(),0).content()).hasSize(2);
        assertThatThrownBy(()->service.update(p.id(),request(rack.getId(),"LOWER",2,p.version()))).hasMessageContaining("已被更新");
        assertThatThrownBy(()->service.delete(p.id(),p.version())).hasMessageContaining("已被更新");
        service.delete(p.id(),updated.version());
        assertThat(service.overview().pallets()).noneMatch(row->row.id().equals(p.id()));
        var history=service.history(p.id(),0).content();assertThat(history).hasSize(3);
        assertThat(history.getFirst().getAction()).isEqualTo("DELETE");assertThat(history.getFirst().getBeforeState()).contains("22334","22335");
        // The vacated physical position can be used again.
        service.create(request(aisle.id(),"RIGHT_2",3,null));
    }
    @Test void occupiedLocationAndIllegalLanesAreRejected(){
        service.create(request(rack.getId(),"LOWER",1,null));
        assertThatThrownBy(()->service.create(request(rack.getId(),"LOWER",1,null))).hasMessageContaining("已有托盘");
        assertThatThrownBy(()->service.create(request(rack.getId(),"LEFT_1",1,null))).hasMessageContaining("无效");
        var aisle=service.createAisle(new WarehouseService.AreaRequest("Aisle-"+UUID.randomUUID(),2,null));
        assertThatThrownBy(()->service.create(request(aisle.id(),"LEFT_2",1,null))).hasMessageContaining("无效");
        assertThatThrownBy(()->service.createAisle(new WarehouseService.AreaRequest("Bad",3,null))).hasMessageContaining("两排或四排");
    }
    @Test void aisleCannotShrinkOrDisappearWhileOccupied(){
        var a=service.createAisle(new WarehouseService.AreaRequest("Aisle-"+UUID.randomUUID(),4,null));
        var p=service.create(request(a.id(),"LEFT_2",1,null));
        assertThatThrownBy(()->service.updateAisle(a.id(),new WarehouseService.AreaRequest(a.name(),2,a.version()))).hasMessageContaining("先移走");
        assertThatThrownBy(()->service.deleteAisle(a.id(),a.version())).hasMessageContaining("先移走");
        service.delete(p.id(),p.version());
        var narrowed=service.updateAisle(a.id(),new WarehouseService.AreaRequest(a.name(),2,a.version()));
        service.deleteAisle(a.id(),narrowed.version());
        assertThatThrownBy(()->service.deleteAisle(rack.getId(),rack.getVersion())).hasMessageContaining("固定库区");
    }
    @Test void inventoryReferencesProtectProductsAndItemOnlyEditAdvancesVersion(){
        var p=service.create(request(rack.getId(),"MIDDLE",2,null));
        assertThatThrownBy(()->products.delete(productId)).hasMessageContaining("在库托盘");
        var edited=service.update(p.id(),new WarehouseService.SaveRequest(p.code(),p.areaId(),p.lane(),p.position(),p.notes(),List.of(new WarehouseService.ItemRequest(productId,"22334",1000L,1)),p.version()));
        assertThat(edited.version()).isGreaterThan(p.version());assertThat(edited.items().getFirst().quantity()).isEqualTo(1000);
        assertThat(service.history(p.id(),0).content().getFirst().getBeforeState()).contains("2000");
    }
    @Test void publicReadAndEveryWriteRouteRequiresLogin() throws Exception {
        var p=service.create(request(rack.getId(),"LOWER",1,null));
        mvc.perform(get("/api/warehouse")).andExpect(status().isOk()).andExpect(jsonPath("$.areas").isArray());
        mvc.perform(get("/api/warehouse/history")).andExpect(status().isOk());
        String body=json.writeValueAsString(request(rack.getId(),"LOWER",2,p.version()));
        mvc.perform(post("/api/warehouse/pallets").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/warehouse/pallets/"+p.id()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/warehouse/pallets/"+p.id()).param("version","0")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/warehouse/aisles").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/warehouse/aisles/1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/warehouse/aisles/1").param("version","0")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/warehouse/pallets").session(session()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.position").value(2));
    }
    @Test void invalidPositionQuantityAndEmptyPalletReturnBadRequest() throws Exception {
        var body=json.valueToTree(request(rack.getId(),"LOWER",1,null));
        ((com.fasterxml.jackson.databind.node.ObjectNode)body).put("position",0);
        mvc.perform(post("/api/warehouse/pallets").session(session()).contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        ((com.fasterxml.jackson.databind.node.ObjectNode)body).put("position",1);
        ((com.fasterxml.jackson.databind.node.ObjectNode)body.get("items").get(0)).put("quantity",-1);
        mvc.perform(post("/api/warehouse/pallets").session(session()).contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        ((com.fasterxml.jackson.databind.node.ObjectNode)body).putArray("items");
        mvc.perform(post("/api/warehouse/pallets").session(session()).contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
    }
}
