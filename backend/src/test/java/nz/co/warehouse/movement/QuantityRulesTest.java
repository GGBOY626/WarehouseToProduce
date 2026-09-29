package nz.co.warehouse.movement;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class QuantityRulesTest {
    @Test void calculatesFullCartonsAndLooseUnits(){assertThat(QuantityRules.calculate(30,18,20)).isEqualTo(560);}
    @Test void calculatesSpecifiedExample(){assertThat(QuantityRules.calculate(18,35,5)).isEqualTo(635);}
    @Test void totalIsUnknownWhenCartonSizeMissing(){assertThat(QuantityRules.calculate(null,10,5)).isNull();}
    @Test void looseOnlyIsKnownWithoutCartonSize(){assertThat(QuantityRules.calculate(null,0,5)).isEqualTo(5);}
    @Test void acceptsCartonsWhenTotalUnknown(){assertThat(QuantityRules.hasQuantity(1,0,null)).isTrue();}
    @Test void rejectsAllZero(){assertThat(QuantityRules.hasQuantity(0,0,null)).isFalse();}
}
