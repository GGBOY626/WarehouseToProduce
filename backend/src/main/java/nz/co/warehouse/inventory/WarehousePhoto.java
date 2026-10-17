package nz.co.warehouse.inventory;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="warehouse_photo") @Getter @Setter @NoArgsConstructor
public class WarehousePhoto {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Lob @Column(nullable=false,columnDefinition="MEDIUMBLOB") private byte[] content;
    @Column(nullable=false) private boolean retained;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
}
