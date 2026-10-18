package nz.co.warehouse.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nz.co.warehouse.common.BusinessException;
import nz.co.warehouse.inventory.WarehouseService;
import nz.co.warehouse.production.ProductionTaskService;
import org.springframework.web.bind.annotation.*;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.*;

@RestController @RequiredArgsConstructor
public class AiToolsController {
    private final AiAccess access;
    private final WarehouseService warehouse;
    private final ProductionTaskService tasks;
    private final ObjectMapper json;
    private static final Set<String> STOCK=Set.of("search_products","get_warehouse_material_summary","get_batch_locations","get_pending_inventory");
    @PostMapping("/internal/ai/tools")
    public Map<String,Object> query(@RequestHeader(value="Authorization",required=false) String authorization,@Valid @RequestBody AiDtos.ToolRequest request){
        access.internal(authorization);var scope=access.resolve(request.ticket());
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("queriedAt",ZonedDateTime.now(ZoneId.of("Pacific/Auckland")).toString());
        result.put("scope",scope);
        if(STOCK.contains(request.name())){
            if(scope.taskId()!=null)throw BusinessException.badRequest("AI_SCOPE","任务范围不能查询全仓，请切换到仓库页面。");
            var f=request.filters();
            if(request.name().equals("get_pending_inventory"))f=new AiDtos.Filters(f.query(),f.batch(),f.row(),f.kind(),f.lane(),f.productId(),true);
            result.put("source","当前已录入托盘台账");
            var inventory=AiInventory.summarize(warehouse.overview(),scope,f);
            // Counts always cover the complete scope, even when detail would exceed the model budget.
            if(json.valueToTree(inventory).toString().length()>22000)
                inventory=new AiInventory.Result(inventory.materialCount(),inventory.palletCount(),inventory.pendingPalletCount(),List.of(),true);
            result.put("inventory",inventory);
        }else if(Set.of("get_production_task_details","get_movement_summary").contains(request.name())){
            if(scope.taskId()==null)throw BusinessException.badRequest("AI_SCOPE","请在生产任务详情页选择任务后分析。");
            var task=json.valueToTree(tasks.detail(scope.taskId()));
            // Allowlist projection; no remarks, photographs, personnel or unrelated history.
            var safe=json.createObjectNode();
            for(String key:List.of("id","name","batchNo","status","progressPercent","targets","output"))safe.set(key,task.get(key));
            var records=safe.putArray("movements");
            for(var row:task.get("movements")){
                var r=records.addObject();
                for(String key:List.of("id","recordNo","direction","returnMovement","status","unknownQuantityCount","movementTime"))r.set(key,row.get(key));
                // Detail is fetched only for a movement-specific question; totals already come from the service.
                if(request.name().equals("get_movement_summary"))r.set("items",row.get("items"));
            }
            result.put("source","生产任务 Service（仅有效流转计入统计）");result.put("task",safe);
        }else throw BusinessException.badRequest("AI_TOOL_INVALID","不支持的只读工具。");
        // Never silently truncate an aggregate or send an unbounded ledger to the model.
        if(json.valueToTree(result).toString().length()>24000)throw BusinessException.badRequest("AI_SCOPE_TOO_LARGE","当前结果过多，请缩小排号、物料或批次范围。");
        return result;
    }
}
