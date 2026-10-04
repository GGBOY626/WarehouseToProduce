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
    @Column(name = "material_code", nullable = false, length = 100) private String materialCode;
    @Column(name = "material_batch", length = 100) private String materialBatch;
    @Enumerated(EnumType.STRING) @Column(name = "movement_direction", length = 40) private ProductUsage movementDirection;
    @Column(name = "default_units_per_carton") private Integer defaultUnitsPerCarton;
    @Column(name = "quantity_unknown", nullable = false) private boolean quantityUnknown;
    @Column(name = "base_unit", length = 20) private String baseUnit = "个";
    @Column(nullable = false) private boolean active = true;
    @Column(name = "active_identity", length = 302, insertable = false, updatable = false) private String activeIdentity;
}
