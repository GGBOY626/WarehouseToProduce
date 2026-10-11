package nz.co.warehouse.movement;

public final class QuantityRules {
    private QuantityRules() {}
    public static Long calculate(Integer unitsPerCarton,int fullCartons,long looseUnits){
        return calculate(unitsPerCarton,fullCartons,0,null,looseUnits);
    }
    public static Long calculate(Integer unitsPerCarton,int fullCartons,int tailCartons,Long tailUnitsPerCarton,long looseUnits){
        long tail=Math.multiplyExact((long)tailCartons,tailUnitsPerCarton==null?0:tailUnitsPerCarton);
        if(fullCartons>0&&unitsPerCarton==null)return null;
        long full=Math.multiplyExact((long)fullCartons,unitsPerCarton==null?0:unitsPerCarton.longValue());
        return Math.addExact(Math.addExact(full,tail),looseUnits);
    }
    public static boolean hasQuantity(int fullCartons,long looseUnits,Long totalUnits){
        return fullCartons>0||looseUnits>0||(totalUnits!=null&&totalUnits>0);
    }
}
