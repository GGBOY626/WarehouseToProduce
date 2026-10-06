package nz.co.warehouse.production;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;
import nz.co.warehouse.product.Product;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="production_task")
public class ProductionTask extends PersistentEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id") private Product product;
    @Column(name="target_quantity",nullable=false) private long targetQuantity;
    @Column(name="planned_date",nullable=false) private LocalDate plannedDate;
    @Column(name="batch_no",nullable=false,length=100) private String batchNo;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status=Status.PREPARING;
    @Column(length=1000) private String remarks;
    public enum Status { PREPARING, IN_PROGRESS, COMPLETED, CANCELLED }
}
