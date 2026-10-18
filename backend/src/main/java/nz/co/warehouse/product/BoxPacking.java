package nz.co.warehouse.product;

import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Embeddable @Data @NoArgsConstructor @AllArgsConstructor
public class BoxPacking {
    @Positive @Column(name="boxes_per_carton",nullable=false) private Integer boxesPerCarton;
    @Positive @Column(name="units_per_box",nullable=false) private Integer unitsPerBox;
    public int cartonUnits() { return Math.multiplyExact(boxesPerCarton, unitsPerBox); }
}
