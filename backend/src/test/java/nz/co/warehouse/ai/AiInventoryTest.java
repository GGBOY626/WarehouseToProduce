package nz.co.warehouse.ai;

import nz.co.warehouse.inventory.WarehouseArea;
import nz.co.warehouse.inventory.WarehouseService.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class AiInventoryTest {
    private final AiDtos.Scope all=new AiDtos.Scope("","","",null,"",false,null);
    private final AiDtos.Filters none=new AiDtos.Filters("","","","","",null,false);
    private final List<AreaResponse> areas=List.of(new AreaResponse(1L,"A",WarehouseArea.Kind.RACK,3,0,null),new AreaResponse(2L,"B",WarehouseArea.Kind.RACK,3,0,null),new AreaResponse(3L,"收货过道",WarehouseArea.Kind.AISLE,4,0,"A"));
    private ItemResponse item(Long id,String name,String code,String batch,Long q,String unit){return new ItemResponse(id,name,code,batch,unit,q,null,"RAW-1",null,null);}
    private ItemResponse item(String batch,Long q){return item(1L,"关节饮","M1",batch,q,"件");}
    private PalletResponse pallet(long id,long area,ItemResponse... items){return new PalletResponse(id,"TP-"+id,area,"",area==3?WarehouseArea.Kind.AISLE:WarehouseArea.Kind.RACK,area==3?"LEFT_1":"UPPER",(int)id,"位置"+id,null,List.of(items),0,null,null);}
    private AiInventory.Result sum(List<PalletResponse> rows){return AiInventory.summarize(new Overview(areas,rows),all,none);}
    @Test void batchesLocationsMultiMaterialAndDedupMatchFrontendSamples(){
        var a=pallet(1,1,item("22344",12000L),item("22345",50000L));
        var b=pallet(2,2,item("22344",8000L),item(2L,"三重幼燃粉","M2","22342",150000L,"件"));
        var result=sum(List.of(a,b,a));var m=result.materials().getFirst();
        assertThat(result.materialCount()).isEqualTo(2);assertThat(result.palletCount()).isEqualTo(2);
        assertThat(m.quantities).containsEntry("件","70000");
        assertThat(m.batches).extracting(v->v.quantities.get("件")).containsExactly("20000","50000");
        assertThat(m.batches.getFirst().locations).hasSize(2);
        assertThat(sum(List.of(pallet(1,1,item("22344",12000L),item("22344",12000L)))).materials().getFirst().quantities).containsEntry("件","24000");
    }
    @Test void unknownUnitAndMixedUnitsAreNotZeroOrConverted(){
        var m=sum(List.of(pallet(1,1,item("22344",null),item("22344",0L),item(1L,"关节饮","M1","22344",5L,"kg"),item(1L,"关节饮","M1","22344",200L,null)))).materials().getFirst();
        assertThat(m.quantities).containsExactlyInAnyOrderEntriesOf(Map.of("件","0","kg","5"));assertThat(m.pending).isEqualTo(1);assertThat(m.unitUnknown).isEqualTo(1);
    }
    @Test void identityMatchesFrontendAmbiguityRules(){
        var result=sum(List.of(pallet(1,1,item("B",12000L),item(null,"关节饮","M1","B",12000L,"件"),item(2L,"关节饮","M1","B",12000L,"件"),item(1L,"关节饮 Plus","M1","B",12000L,"件"),item(null,"关节饮","M9","B",12000L,"件"),item(null,"关节饮",null,"B",12000L,"件"))));
        assertThat(result.materialCount()).isEqualTo(6);
        assertThat(sum(List.of(pallet(1,1,item("B",12000L),item(null,"关节饮","M1","B",12000L,"件")))).materials().getFirst().quantities).containsEntry("件","24000");
    }
    @Test void missingBatchesStaySeparateAndBigTotalsStayExact(){
        var m=sum(List.of(pallet(1,1,item(null,9007199254740991L),item(" ",9007199254740991L)))).materials().getFirst();
        assertThat(m.quantities).containsEntry("件","18014398509481982");assertThat(m.batches).hasSize(2);assertThat(m.batches).allMatch(b->b.missing);
    }
    @Test void scopeIntersectionAndItemFiltersCannotLeakOtherPalletItems(){
        var rows=List.of(pallet(1,1,item("22344",12000L),item(2L,"外盒","BOX","B2",null,"个")),pallet(2,2,item("22344",8000L)),pallet(3,3,item("22344",500L)));
        var scope=new AiDtos.Scope("22344","","A",null,"",false,null);
        assertThat(AiInventory.summarize(new Overview(areas,rows),scope,none).materials().getFirst().quantities).containsEntry("件","12500");
        var forbidden=new AiDtos.Filters("","","B","","",null,false);
        assertThat(AiInventory.summarize(new Overview(areas,rows),scope,forbidden).materialCount()).isZero();
        var pending=new AiDtos.Filters("关节饮","","","","",null,true);
        assertThat(AiInventory.summarize(new Overview(areas,rows),all,pending).materialCount()).isZero();
        var boxes=new AiDtos.Filters("B2","","","","",null,false);
        assertThat(AiInventory.summarize(new Overview(areas,rows),all,boxes).materials()).extracting(m->m.name).containsExactly("外盒");
    }
}
