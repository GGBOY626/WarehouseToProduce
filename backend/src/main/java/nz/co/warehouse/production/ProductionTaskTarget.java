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
    @ElementCollection @CollectionTable(name="production_task_target_material_code",joinColumns=@JoinColumn(name="target_id")) @Column(name="material_code",nullable=false,length=100) private java.util.Set<String> materialCodes=new java.util.TreeSet<>();
    @ElementCollection @CollectionTable(name="production_task_target_production_number",joinColumns=@JoinColumn(name="target_id")) @Column(name="production_number",nullable=false,length=100) private java.util.Set<String> productionNumbers=new java.util.TreeSet<>();
    @ElementCollection @CollectionTable(name="production_task_target_material_batch",joinColumns=@JoinColumn(name="target_id")) @Column(name="batch_no",nullable=false,length=100) private java.util.Set<String> materialBatches=new java.util.TreeSet<>();
    @Column(name="target_quantity",nullable=false) private long targetQuantity;
    @Column(name="sort_order",nullable=false) private int sortOrder;
    @org.hibernate.annotations.ColumnDefault("false")
    @Column(name="found",nullable=false) private boolean found;
    @Column(name="found_location",length=255) private String foundLocation;
}
