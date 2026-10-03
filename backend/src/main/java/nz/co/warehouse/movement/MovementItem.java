package nz.co.warehouse.movement;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;
import nz.co.warehouse.product.Product;
import org.hibernate.annotations.BatchSize;
import java.util.*;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="movement_item")
public class MovementItem extends PersistentEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="movement_id") private Movement movement;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id") private Product product;
    @Column(name="product_name_snapshot",nullable=false,length=200) private String productNameSnapshot;
    @Column(name="sku_snapshot",length=100) private String skuSnapshot;
    @Column(name="units_per_carton_snapshot") private Integer unitsPerCartonSnapshot;
    @Column(name="base_unit_snapshot",nullable=false,length=20) private String baseUnitSnapshot;
    @Column(name="batch_no",nullable=false,length=100) private String batchNo;
    @Column(name="full_cartons",nullable=false) private int fullCartons;
    @Column(name="loose_units",nullable=false) private long looseUnits;
    @Column(name="calculated_total_units") private Long calculatedTotalUnits;
    @Column(name="total_units") private Long totalUnits;
    @Column(name="total_units_overridden",nullable=false) private boolean totalUnitsOverridden;
    @Column(name="quantity_unknown",nullable=false) private boolean quantityUnknown;
    @Column(length=1000) private String remarks;
    @Column(name="sort_order",nullable=false) private int sortOrder;
    @OneToMany(mappedBy="movementItem",cascade=CascadeType.ALL,orphanRemoval=true)
    @BatchSize(size=100)
    private Set<MovementItemIssue> issues=new LinkedHashSet<>();
    public void addIssue(MovementItemIssue issue){issues.add(issue);issue.setMovementItem(this);}
}
