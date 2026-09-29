package nz.co.warehouse.product;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "product")
public class Product extends PersistentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200) private String name;
    @Column(length = 100) private String sku;
    @Column(name = "default_units_per_carton") private Integer defaultUnitsPerCarton;
    @Column(name = "base_unit", nullable = false, length = 20) private String baseUnit = "个";
    @Column(nullable = false) private boolean active = true;
    @Column(name = "active_name", insertable = false, updatable = false) private String activeName;
}
