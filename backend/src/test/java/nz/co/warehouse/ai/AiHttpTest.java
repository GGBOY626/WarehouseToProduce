package nz.co.warehouse.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import nz.co.warehouse.auth.*;
import nz.co.warehouse.common.GlobalExceptionHandler;
import nz.co.warehouse.inventory.*;
import nz.co.warehouse.production.ProductionTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiHttpTest {
    @Test void badAiConfigurationDoesNotBlockWarehouseReadOrWrite() throws Exception {
        for(String token:List.of("", "test-only-token-longer-than-32-characters")) {
            var access=new AiAccess();
            ReflectionTestUtils.setField(access,"token",token);ReflectionTestUtils.setField(access,"enabled",true);
            var warehouse=mock(WarehouseService.class);
            when(warehouse.overview()).thenReturn(new WarehouseService.Overview(List.of(),List.of()));
            var mvc=MockMvcBuilders.standaloneSetup(new AiController(access,"http://[invalid"),new WarehouseController(warehouse,mock(WarehousePhotoService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).addInterceptors(new WriteAuthorizationInterceptor()).build();
            var session=new MockHttpSession();session.setAttribute(AuthController.AUTH_SESSION_KEY,true);
            mvc.perform(post("/api/ai/chat").session(session).contentType("application/json").content("{\"message\":\"inventory\",\"scope\":{}}"))
                .andExpect(status().isServiceUnavailable());
            mvc.perform(get("/api/warehouse")).andExpect(status().isOk());
            mvc.perform(post("/api/warehouse/aisles").session(session).contentType("application/json").content("{\"name\":\"Test aisle\",\"lanes\":2}"))
                .andExpect(status().isOk());
            verify(warehouse).createAisle(any());
        }
    }
    @Test void productionProjectionUsesExistingServiceAndExcludesSensitiveFields() throws Exception {
        var access=new AiAccess();String token="test-only-token-longer-than-32-characters";
        ReflectionTestUtils.setField(access,"token",token);ReflectionTestUtils.setField(access,"enabled",true);
        var tasks=mock(ProductionTaskService.class);var mapper=new ObjectMapper();
        var response=mapper.readValue("""
            {"id":42,"name":"Task","status":"IN_PROGRESS","remarks":"private note","progressPercent":50,
             "targets":[{"productId":1,"issuedQuantity":12000,"returnedQuantity":2000,"completedQuantity":10000,"shortageQuantity":10000}],
             "output":{"receivedQuantity":300,"producedQuantity":9000,"productionPercent":90,"lossPercent":10,"provisional":true},
             "movements":[{"id":5,"recordNo":"TR5","status":"VOID","unknownQuantityCount":0}]}
            """,ProductionTaskService.Response.class);
        when(tasks.detail(42L)).thenReturn(response);
        var req=new MockHttpServletRequest();req.getSession().setAttribute(AuthController.AUTH_SESSION_KEY,true);
        String ticket=access.issue(req,new AiDtos.Scope("","","",null,"",false,42L));
        var mvc=MockMvcBuilders.standaloneSetup(new AiToolsController(access,mock(WarehouseService.class),tasks,mapper)).setControllerAdvice(new GlobalExceptionHandler()).build();
        String body="{\"ticket\":\""+ticket+"\",\"name\":\"get_production_task_details\",\"filters\":{}}";
        mvc.perform(post("/internal/ai/tools").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/internal/ai/tools").header("Authorization","Bearer "+token).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.task.remarks").doesNotExist())
            .andExpect(jsonPath("$.task.targets[0].completedQuantity").value(10000))
            .andExpect(jsonPath("$.task.output.productionPercent").value(90))
            .andExpect(jsonPath("$.task.output.provisional").value(true))
            .andExpect(jsonPath("$.task.movements[0].status").value("VOID"));
        verify(tasks).detail(42L);
        mvc.perform(post("/internal/ai/tools").header("Authorization","Bearer "+token).contentType("application/json").content(body.replace("get_production_task_details","get_warehouse_material_summary"))).andExpect(status().isBadRequest());
    }
    @Test void aiOutageDoesNotBreakWarehouseAndInternalEndpointNeedsBothCredentials() throws Exception {
        var access=new AiAccess();String token="test-only-token-longer-than-32-characters";
        ReflectionTestUtils.setField(access,"token",token);ReflectionTestUtils.setField(access,"enabled",true);
        var warehouse=mock(WarehouseService.class);when(warehouse.overview()).thenReturn(new WarehouseService.Overview(List.of(),List.of()));
        var mvc=MockMvcBuilders.standaloneSetup(new AiController(access,"http://127.0.0.1:1"),new AiToolsController(access,warehouse,mock(ProductionTaskService.class),new ObjectMapper()),new WarehouseController(warehouse,mock(WarehousePhotoService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).addInterceptors(new WriteAuthorizationInterceptor()).build();
        String body="{\"message\":\"查询库存\",\"scope\":{\"row\":\"A\"}}";
        mvc.perform(post("/api/ai/chat").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        var session=new MockHttpSession();session.setAttribute(AuthController.AUTH_SESSION_KEY,true);
        mvc.perform(post("/api/ai/chat").session(session).contentType("application/json").content(body)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/warehouse")).andExpect(status().isOk());
        // Standalone interceptor also covers /internal here; a logged-in user still cannot bypass service auth.
        String query="{\"ticket\":\"forged\",\"name\":\"get_warehouse_material_summary\",\"filters\":{}}";
        mvc.perform(post("/internal/ai/tools").session(session).contentType("application/json").content(query)).andExpect(status().isForbidden());
        mvc.perform(post("/internal/ai/tools").session(session).header("Authorization","Bearer "+token).contentType("application/json").content(query)).andExpect(status().isForbidden());
        var req=new MockHttpServletRequest();req.setSession(session);
        String ticket=access.issue(req,new AiDtos.Scope("","","A",null,"",false,null));
        mvc.perform(post("/internal/ai/tools").session(session).header("Authorization","Bearer "+token).contentType("application/json").content(query.replace("forged",ticket)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.inventory.materialCount").value(0));
        mvc.perform(post("/internal/ai/tools").session(session).header("Authorization","Bearer "+token).contentType("application/json").content(query.replace("forged",ticket).replace("get_warehouse_material_summary","get_production_task_details")))
            .andExpect(status().isBadRequest());
    }
}
