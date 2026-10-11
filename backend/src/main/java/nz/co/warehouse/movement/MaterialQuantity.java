package nz.co.warehouse.movement;

import java.util.*;

/** Shared read-side totals for lists, statistics and PDF; saved totals (including overrides) are authoritative. */
public final class MaterialQuantity {
    public record Key(String materialCode,String materialBatch,String baseUnit) {
        public static Key of(MovementItem item){
            return new Key(item.getBatchNo(),item.getMovement().getManufactureLot(),item.getBaseUnitSnapshot());
        }
    }
    public record Packaging(String type,Long unitsPerCarton,long cartons) {}
    private record Spec(String type,Long unitsPerCarton) {}
    private final Map<Spec,Long> specs=new LinkedHashMap<>();
    private long fullCartons,tailCartons,looseUnits,totalQuantity,unknownItemCount;

    public void add(MovementItem item){
        fullCartons=Math.addExact(fullCartons,item.getFullCartons());
        tailCartons=Math.addExact(tailCartons,item.getTailCartons());
        looseUnits=Math.addExact(looseUnits,item.getLooseUnits());
        if(item.isQuantityUnknown()||item.getTotalUnits()==null)unknownItemCount++;
        else totalQuantity=Math.addExact(totalQuantity,item.getTotalUnits());
        addSpec("FULL",item.getUnitsPerCartonSnapshot()==null?null:item.getUnitsPerCartonSnapshot().longValue(),item.getFullCartons());
        addSpec("TAIL",item.getTailUnitsPerCarton(),item.getTailCartons());
    }
    private void addSpec(String type,Long units,long cartons){
        if(cartons>0)specs.merge(new Spec(type,units),cartons,Math::addExact);
    }
    public long fullCartons(){return fullCartons;}
    public long tailCartons(){return tailCartons;}
    public long totalCartons(){return Math.addExact(fullCartons,tailCartons);}
    public long looseUnits(){return looseUnits;}
    public long totalQuantity(){return totalQuantity;}
    public long unknownItemCount(){return unknownItemCount;}
    public List<Packaging> packaging(){return specs.entrySet().stream().map(e->new Packaging(e.getKey().type(),e.getKey().unitsPerCarton(),e.getValue())).toList();}

    public static Map<Key,List<MovementItem>> group(List<MovementItem> items){
        Map<Key,List<MovementItem>> groups=new LinkedHashMap<>();
        items.forEach(item->groups.computeIfAbsent(Key.of(item),ignored->new ArrayList<>()).add(item));
        return groups;
    }
    public static MaterialQuantity of(List<MovementItem> items){
        MaterialQuantity result=new MaterialQuantity();items.forEach(result::add);return result;
    }
}
