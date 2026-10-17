package nz.co.warehouse.inventory;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;

@Entity @Table(name="warehouse_area") @Getter @Setter @NoArgsConstructor
public class WarehouseArea extends PersistentEntity {
    public enum Kind { RACK, AISLE }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private Kind kind;
    @Column(nullable=false,unique=true,length=60) private String name;
    @Column(nullable=false) private int lanes;
    @Version private long version;
}
