package nz.co.warehouse.movement;

import java.util.*;

/** Shared read-side totals for lists, statistics and PDF; saved totals (including overrides) are authoritative. */
public final class MaterialQuantity {
    public record Key(String materialCode,String materialBatch,String baseUnit,String subBatch) {
        public static Key of(MovementItem item){
            return new Key(item.getBatchNo(),item.effectiveMaterialBatch(),item.getBaseUnitSnapshot(),item.getSubBatch()==null||item.getSubBatch().isBlank()?null:item.getSubBatch().trim());
        }
    }
    public record Packaging(String type,Long unitsPerCarton,long cartons) {}
    private record Spec(String type,Long unitsPerCarton) {}
    private final Map<Spec,Long> specs=new LinkedHashMap<>();
    private long fullCartons,tailCartons,looseUnits,totalQuantity,unknownItemCount,totalBoxes;
    private boolean boxCountKnown=true,hasBoxPacking;

    public void add(MovementItem item){
        fullCartons=Math.addExact(fullCartons,item.getFullCartons());
        tailCartons=Math.addExact(tailCartons,item.getTailCartons());
        looseUnits=Math.addExact(looseUnits,item.getLooseUnits());
        if(item.isQuantityUnknown()||item.getTotalUnits()==null)unknownItemCount++;
        else totalQuantity=Math.addExact(totalQuantity,item.getTotalUnits());
        if(item.getTotalCartons()>0){
            Integer boxesPerCarton=item.getBoxesPerCartonSnapshot(),unitsPerBox=item.getUnitsPerBoxSnapshot();
            Long tailUnits=item.getTailUnitsPerCarton();
            if(boxesPerCarton==null||unitsPerBox==null||unitsPerBox<=0||item.getTailCartons()>0&&(tailUnits==null||tailUnits%unitsPerBox!=0))boxCountKnown=false;
            else{
                hasBoxPacking=true;
                totalBoxes=Math.addExact(totalBoxes,Math.addExact(Math.multiplyExact((long)item.getFullCartons(),boxesPerCarton),
                        Math.multiplyExact((long)item.getTailCartons(),tailUnits==null?0:tailUnits/unitsPerBox)));
            }
        }
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
    public Long totalBoxes(){return boxCountKnown&&hasBoxPacking?totalBoxes:null;}
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
