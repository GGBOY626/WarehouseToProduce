package nz.co.warehouse.ai;

import nz.co.warehouse.inventory.WarehouseService;
import nz.co.warehouse.inventory.WarehouseArea;
import java.math.BigInteger;
import java.util.*;

/** Mirrors warehouseSummary.ts: full-ledger identity, then item-level filters. */
public final class AiInventory {
    private AiInventory() {}
    public static class Amounts {
        public final Map<String,String> quantities = new TreeMap<>();
        public int pending, unitUnknown;
        public final Set<Long> palletIds = new LinkedHashSet<>();
        void add(WarehouseService.ItemResponse i, long id) {
            palletIds.add(id);
            if(i.quantity()==null || i.quantity()<0 || i.quantity()>9007199254740991L){pending++;return;}
            String unit=clean(i.unit());
            if(unit.isEmpty()){unitUnknown++;return;}
            quantities.put(unit,new BigInteger(quantities.getOrDefault(unit,"0")).add(BigInteger.valueOf(i.quantity())).toString());
        }
    }
    public static final class Material extends Amounts {
        public String name, code; public Long productId;
        public final List<Batch> batches=new ArrayList<>();
    }
    public static final class Batch extends Amounts {
        public String label; public boolean missing;
        public final List<Location> locations=new ArrayList<>();
    }
    public static final class Location extends Amounts {
        public String location; public Long areaId; public String lane; public int position;
        public final Set<String> pallets=new LinkedHashSet<>(), rawBatches=new LinkedHashSet<>();
    }
    public record Result(int materialCount,int palletCount,int pendingPalletCount,List<Material> materials,boolean detailsOmitted) {}
    public static Result summarize(WarehouseService.Overview data,AiDtos.Scope scope,AiDtos.Filters filters) {
        var unique=new LinkedHashMap<Long,WarehouseService.PalletResponse>();
        data.pallets().forEach(p->unique.put(p.id(),p));
        var catalog=new HashMap<List<String>,Set<Long>>();
        unique.values().forEach(p->p.items().forEach(i->{if(i.productId()!=null)catalog.computeIfAbsent(signature(i),k->new HashSet<>()).add(i.productId());}));
        var areas=new HashMap<Long,WarehouseService.AreaResponse>(); data.areas().forEach(a->areas.put(a.id(),a));
        var materials=new LinkedHashMap<List<String>,Material>();
        Set<Long> pallets=new HashSet<>(),pending=new HashSet<>();
        for(var p:unique.values()) {
            var a=areas.get(p.areaId());
            String row=a==null?"":a.kind()==WarehouseArea.Kind.RACK?a.name():clean(a.rackRow());
            if(!matches(scope.row(),row)||!matches(filters.row(),row)||!matches(scope.kind(),p.kind().name())||!matches(filters.kind(),p.kind().name())||
               !matches(scope.lane(),p.lane())||!matches(filters.lane(),p.lane())||scope.areaId()!=null&&!scope.areaId().equals(p.areaId()))continue;
            for(var i:p.items()) {
                Boolean finished=scope.finished()!=null?scope.finished():scope.hideFinished()?Boolean.FALSE:null;
                if(finished!=null&&i.finished()!=finished)continue;
                if((scope.unknownOnly()||filters.unknownOnly())&&i.quantity()!=null)continue;
                if(!search(scope.query(),i,p)||!search(filters.query(),i,p)||!matches(filters.batch(),clean(i.batch())))continue;
                var ids=clean(i.materialCode()).isEmpty()?null:catalog.get(signature(i));
                Long productId=i.productId()!=null?i.productId():ids!=null&&ids.size()==1?ids.iterator().next():null;
                if(filters.productId()!=null&&!filters.productId().equals(productId))continue;
                var key=List.of(clean(i.productName()),productId==null?"manual":"product",productId==null?clean(i.materialCode()):productId.toString());
                var m=materials.computeIfAbsent(key,k->{var v=new Material();v.name=clean(i.productName());v.code=clean(i.materialCode());v.productId=productId;return v;});
                String label=clean(i.batch());
                var b=label.isEmpty()?null:m.batches.stream().filter(v->v.label.equals(label)).findFirst().orElse(null);
                if(b==null){b=new Batch();b.label=label.isEmpty()?"批号未录入":label;b.missing=label.isEmpty();m.batches.add(b);}
                var l=b.locations.stream().filter(v->v.areaId.equals(p.areaId())&&v.lane.equals(p.lane())&&v.position==p.position()).findFirst().orElse(null);
                if(l==null){l=new Location();l.location=p.location();l.areaId=p.areaId();l.lane=p.lane();l.position=p.position();b.locations.add(l);}
                l.pallets.add(p.code());if(!clean(i.rawMaterialBatch()).isEmpty())l.rawBatches.add(clean(i.rawMaterialBatch()));
                m.add(i,p.id());b.add(i,p.id());l.add(i,p.id());pallets.add(p.id());if(i.quantity()==null)pending.add(p.id());
            }
        }
        return new Result(materials.size(),pallets.size(),pending.size(),new ArrayList<>(materials.values()),false);
    }
    private static String clean(String s){return s==null?"":s.trim();}
    private static List<String> signature(WarehouseService.ItemResponse i){return List.of(clean(i.productName()),clean(i.materialCode()));}
    private static boolean matches(String filter,String value){return clean(filter).isEmpty()||clean(filter).equals(value);}
    private static boolean search(String query,WarehouseService.ItemResponse i,WarehouseService.PalletResponse p){
        String q=clean(query).toLowerCase(Locale.ROOT);
        return q.isEmpty()||Arrays.asList(i.productName(),i.materialCode(),i.batch(),i.rawMaterialBatch(),p.code(),p.location(),p.notes()).stream().anyMatch(s->s!=null&&s.toLowerCase(Locale.ROOT).contains(q));
    }
}
