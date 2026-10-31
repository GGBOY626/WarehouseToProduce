package nz.co.warehouse.production;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="production_task_photo")
public class ProductionTaskPhoto extends PersistentEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="production_task_id") private ProductionTask task;
    @Column(name="file_path",nullable=false,length=500) private String filePath;
    @Column(name="original_name",nullable=false,length=255) private String originalName;
    @Column(name="mime_type",nullable=false,length=100) private String mimeType;
    @Column(name="file_size",nullable=false) private long fileSize;
    @Column(nullable=false) private int width;
    @Column(nullable=false) private int height;
}
