package nz.co.warehouse.photo;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;
import nz.co.warehouse.movement.MovementEnums;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="record_original")
public class RecordOriginal extends PersistentEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private LocalDate recordDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private MovementEnums.Direction direction;
    @Column(nullable=false,length=500) private String filePath;
    @Column(nullable=false,length=255) private String originalName;
    @Column(nullable=false,length=100) private String mimeType;
}
