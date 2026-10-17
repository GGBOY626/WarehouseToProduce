package nz.co.warehouse.inventory;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="warehouse_event") @Getter @Setter @NoArgsConstructor
public class WarehouseEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="pallet_id") private Long palletId;
    @Column(name="pallet_code",length=60) private String palletCode;
    @Column(nullable=false,length=20) private String action;
    @Column(nullable=false,length=2000) private String description;
    @Column(name="before_state",columnDefinition="TEXT") private String beforeState;
    @Column(name="after_state",columnDefinition="TEXT") private String afterState;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
}
