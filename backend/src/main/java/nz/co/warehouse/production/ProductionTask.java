package nz.co.warehouse.production;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;
import nz.co.warehouse.product.Product;
import java.time.LocalDate;
import java.util.*;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="production_task")
public class ProductionTask extends PersistentEntity {
    @Column(name="output_material_batch",length=100) private String outputMaterialBatch;
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id") private Product product;
    @Column(name="target_quantity",nullable=false) private long targetQuantity;
    @Column(name="planned_date",nullable=false) private LocalDate plannedDate;
    @Column(name="batch_no",length=100) private String batchNo;
    @Column(name="order_no",length=100) private String orderNo;
    @Column(name="feishu_no",length=100) private String feishuNo;
    @Column(name="planned_quantity") private Long plannedQuantity;
    @Column(name="product_batch",length=100) private String productBatch;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status=Status.IN_PROGRESS;
    @Column(length=100) private String name;
    @Column(length=1000) private String remarks;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="output_product_id") private Product outputProduct;
    @Column(name="output_boxes_per_carton") private Integer outputBoxesPerCarton;
    @Column(name="output_units_per_box") private Integer outputUnitsPerBox;
    @Column(name="output_cartons_per_pallet") private Integer outputCartonsPerPallet;
    @Column(name="yield_material_quantity") private Long yieldMaterialQuantity;
    @Column(name="yield_output_quantity") private Long yieldOutputQuantity;
    @Enumerated(EnumType.STRING) @Column(name="output_quantity_basis",length=10) private OutputQuantityBasis outputQuantityBasis;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="primary_material_product_id") private Product primaryMaterial;
    public enum OutputQuantityBasis { BOX, UNIT }
    public Integer outputCartonUnits(){
        if(outputBoxesPerCarton==null||outputUnitsPerBox==null||outputQuantityBasis==null)return null;
        return outputQuantityBasis==OutputQuantityBasis.BOX?outputBoxesPerCarton:Math.multiplyExact(outputBoxesPerCarton,outputUnitsPerBox);
    }
    @Column(name="target_output_quantity") private Long targetOutputQuantity;
    @OneToMany(mappedBy="task",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductionTaskTarget> targets=new ArrayList<>();
    public String displayName(){
        if(name!=null&&!name.isBlank())return name;
        String productName=outputProduct!=null?outputProduct.getName():product.getName();
        return batchNo==null||batchNo.isBlank()?productName:productName+" · "+batchNo;
    }
    public enum Status { PREPARING, IN_PROGRESS, COMPLETED, CANCELLED }
}
