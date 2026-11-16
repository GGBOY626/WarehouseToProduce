package nz.co.warehouse.inventory;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;

@Entity @Table(name="third_party_warehouse")
@Getter @Setter @NoArgsConstructor
public class ThirdPartyWarehouse extends PersistentEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=100) private String name;
    @Column(nullable=false,length=500) private String location;
    @Version private long version;
}
