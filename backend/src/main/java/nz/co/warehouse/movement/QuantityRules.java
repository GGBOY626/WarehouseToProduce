package nz.co.warehouse.movement;

public final class QuantityRules {
    private QuantityRules() {}
    public static Long calculate(Integer unitsPerCarton,int fullCartons,long looseUnits){
        if(unitsPerCarton!=null)return Math.addExact(Math.multiplyExact((long)fullCartons,unitsPerCarton.longValue()),looseUnits);
        return fullCartons==0?looseUnits:null;
    }
    public static boolean hasQuantity(int fullCartons,long looseUnits,Long totalUnits){
        return fullCartons>0||looseUnits>0||(totalUnits!=null&&totalUnits>0);
    }
}
