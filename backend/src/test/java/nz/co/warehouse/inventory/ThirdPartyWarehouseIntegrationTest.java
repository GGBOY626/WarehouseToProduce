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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:third_party;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"})
@AutoConfigureMockMvc
class ThirdPartyWarehouseIntegrationTest {
    @Autowired ThirdPartyWarehouseService service;
    @Autowired ProductService products;
    @Autowired WarehouseService warehouseService;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    ThirdPartyWarehouseService.WarehouseResponse warehouse;
    static final String BASE="/api/warehouse/third-party";
    @BeforeEach void setup(){warehouse=service.createWarehouse(new ThirdPartyWarehouseService.WarehouseRequest("外仓-"+UUID.randomUUID(),"北区 1 号",null));}
    MockHttpSession session(){var s=new MockHttpSession();s.setAttribute(AuthController.AUTH_SESSION_KEY,true);return s;}
    Long product(ProductUsage direction,boolean multiple){
        String code="M-"+UUID.randomUUID();
        return products.create(new ProductDtos.Request("原料-"+UUID.randomUUID(),code,null,"B-001",direction,null,"条",false,List.of(),multiple?List.of("B-002"):List.of(),List.of(),multiple?List.of("P-001","P-002"):List.of("P-001"),multiple?List.of(code,"M-SECOND"):List.of(code))).id();
    }
    ThirdPartyWarehouseService.ItemRequest manual(){return new ThirdPartyWarehouseService.ItemRequest(null,"直接放外仓的货","MANUAL-CODE","MANUAL-P","MANUAL-B","条",null,3);}
    ThirdPartyWarehouseService.StockRequest request(String number,Long version,ThirdPartyWarehouseService.ItemRequest... items){
        return new ThirdPartyWarehouseService.StockRequest(warehouse.id(),number,"直接存放或已发往外仓",List.of(items),version);
    }
    ThirdPartyWarehouseService.ItemRequest linked(Long productId){return new ThirdPartyWarehouseService.ItemRequest(productId,null,null,null,null,null,100L,2);}
    @Test void allWritesRequireLoginAndReadsArePublic() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isOk());
        for(String path:List.of("/warehouses","/stocks")){
            mvc.perform(post(BASE+path).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
            mvc.perform(put(BASE+path+"/1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
            mvc.perform(delete(BASE+path+"/1").param("version","0")).andExpect(status().isUnauthorized());
        }
    }
    @Test void linkedProductsFillIdentifiersAndManualMaterialsDoNotCreateProducts() throws Exception {
        Long id=product(ProductUsage.WAREHOUSE_TO_PRODUCTION,false);
        var result=mvc.perform(post(BASE+"/stocks").session(session()).contentType("application/json").content(json.writeValueAsString(request("00123",null,linked(id),manual()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.warehouseNumber").value("00123")).andExpect(jsonPath("$.warehouseLocation").value("北区 1 号"))
            .andExpect(jsonPath("$.items[0].productionNumber").value("P-001")).andExpect(jsonPath("$.items[0].materialBatch").value("B-001"))
            .andExpect(jsonPath("$.items[1].productId").isEmpty()).andExpect(jsonPath("$.items[1].quantity").isEmpty()).andReturn();
        var stock=json.readValue(result.getResponse().getContentAsString(),ThirdPartyWarehouseService.StockResponse.class);
        assertThat(stock.items().getFirst().materialCode()).startsWith("M-");
        assertThat(products.search("直接放外仓的货",true,null)).isEmpty();
        assertThat(service.overview().stocks()).anyMatch(s->s.id().equals(stock.id())&&s.items().size()==2);
    }
    @Test void wrongDirectionAndAmbiguousIdentifiersAreRejected(){
        Long wrong=product(ProductUsage.PRODUCTION_TO_WAREHOUSE,false),multiple=product(ProductUsage.WAREHOUSE_TO_PRODUCTION,true);
        assertThatThrownBy(()->service.createStock(request("wrong",null,linked(wrong)))).hasMessageContaining("不适用于");
        assertThatThrownBy(()->service.createStock(request("multiple",null,linked(multiple)))).hasMessageContaining("多个物料编码");
        var selected=new ThirdPartyWarehouseService.ItemRequest(multiple,null,"M-SECOND","P-002","B-002","条",50L,null);
        var stock=service.createStock(request("multiple",null,selected));
        assertThat(stock.items().getFirst().productionNumber()).isEqualTo("P-002");
        assertThat(stock.items().getFirst().materialBatch()).isEqualTo("B-002");
    }
    @Test void warehouseNumberIsUniqueWithinWarehouseAndRetainedAsText(){
        var stock=service.createStock(request(" 00123-A ",null,manual()));
        assertThat(stock.warehouseNumber()).isEqualTo("00123-A");
        assertThatThrownBy(()->service.createStock(request("00123-a",null,manual()))).hasMessageContaining("已存在该仓库号");
        var second=service.createWarehouse(new ThirdPartyWarehouseService.WarehouseRequest("另一个仓库","南区",null));
        assertThat(service.createStock(new ThirdPartyWarehouseService.StockRequest(second.id(),"00123-A",null,List.of(manual()),null)).warehouseId()).isEqualTo(second.id());
    }
    @Test void stockOnlyEditsAdvanceVersionAndKeepAuditSnapshots(){
        var stock=service.createStock(request("OLD-001",null,manual()));
        var changed=new ThirdPartyWarehouseService.ItemRequest(null,"直接放外仓的货","NEW-CODE","NEW-P","NEW-B","条",99L,1);
        var edited=service.updateStock(stock.id(),request(stock.warehouseNumber(),stock.version(),changed));
        assertThat(edited.version()).isGreaterThan(stock.version());
        assertThatThrownBy(()->service.updateStock(stock.id(),request("stale",stock.version(),manual()))).hasMessageContaining("已被更新");
        assertThatThrownBy(()->service.deleteStock(stock.id(),stock.version())).hasMessageContaining("已被更新");
        service.deleteStock(stock.id(),edited.version());
        assertThat(service.overview().stocks()).noneMatch(s->s.id().equals(stock.id()));
        assertThat(warehouseService.history(null,0).content()).anySatisfy(e->{
            assertThat(e.getAction()).isEqualTo("EXT_STOCK_DELETE");assertThat(e.getBeforeState()).contains("OLD-001","NEW-P","NEW-B");
        });
    }
    @Test void cannotDeleteOccupiedWarehouseOrReferencedProductAndWarehouseEditsAreVersioned(){
        Long productId=product(ProductUsage.WAREHOUSE_TO_PRODUCTION,false);
        var stock=service.createStock(request("in-use",null,linked(productId)));
        assertThatThrownBy(()->service.deleteWarehouse(warehouse.id(),warehouse.version())).hasMessageContaining("仍有库存");
        assertThatThrownBy(()->products.delete(productId)).hasMessageContaining("第三方库存");
        var edited=service.updateWarehouse(warehouse.id(),new ThirdPartyWarehouseService.WarehouseRequest("更新后的仓库","新区",warehouse.version()));
        assertThatThrownBy(()->service.updateWarehouse(warehouse.id(),new ThirdPartyWarehouseService.WarehouseRequest("旧版本","旧地址",warehouse.version()))).hasMessageContaining("已被更新");
        assertThat(service.overview().stocks()).anySatisfy(s->{assertThat(s.id()).isEqualTo(stock.id());assertThat(s.warehouseLocation()).isEqualTo("新区");});
        service.deleteStock(stock.id(),stock.version());service.deleteWarehouse(warehouse.id(),edited.version());
    }
    @Test void validatesRequiredNumberNamesCountsAndItemLimit() throws Exception {
        for(Object input:List.of(
            request(" ",null,manual()),
            request("empty",null),
            request("no-name",null,new ThirdPartyWarehouseService.ItemRequest(null," ",null,null,null,null,null,null)),
            request("negative",null,new ThirdPartyWarehouseService.ItemRequest(null,"货",null,null,null,null,-1L,0)),
            request("overflow",null,new ThirdPartyWarehouseService.ItemRequest(null,"货",null,null,null,null,9007199254740992L,0)),
            new ThirdPartyWarehouseService.StockRequest(warehouse.id(),"too-many",null,Collections.nCopies(51,manual()),null)
        )){
            mvc.perform(post(BASE+"/stocks").session(session()).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isBadRequest());
        }
        mvc.perform(post(BASE+"/warehouses").session(session()).contentType("application/json").content("{\"name\":\"外仓\",\"location\":\" \"}")).andExpect(status().isBadRequest());
    }
}
