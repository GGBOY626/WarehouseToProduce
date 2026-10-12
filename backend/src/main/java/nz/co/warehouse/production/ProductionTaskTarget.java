package nz.co.warehouse.production;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.product.Product;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="production_task_target",uniqueConstraints=@UniqueConstraint(columnNames={"production_task_id","product_id","material_batch"}))
public class ProductionTaskTarget {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="production_task_id") private ProductionTask task;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id") private Product product;
    @Column(name="material_batch",nullable=false,length=100) private String materialBatch = "";
    @Column(name="target_quantity",nullable=false) private long targetQuantity;
    @Column(name="sort_order",nullable=false) private int sortOrder;
}
