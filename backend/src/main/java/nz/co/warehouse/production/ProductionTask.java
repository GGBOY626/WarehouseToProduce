package nz.co.warehouse.production;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;
import nz.co.warehouse.product.Product;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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
    @OneToMany(mappedBy="task",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductionTaskTarget> targets=new ArrayList<>();
    public String displayName(){return targets.stream().map(t->t.getProduct().getName()).collect(Collectors.joining("、"));}
    public boolean containsTarget(long productId){return targets.stream().anyMatch(t->t.getProduct().getId().equals(productId));}
    public enum Status { PREPARING, IN_PROGRESS, COMPLETED, CANCELLED }
}
