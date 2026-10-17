package nz.co.warehouse.inventory;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;
import nz.co.warehouse.product.Product;
import java.util.*;

@Entity @Table(name="warehouse_pallet",uniqueConstraints=@UniqueConstraint(columnNames={"area_id","lane","position_no"}))
@Getter @Setter @NoArgsConstructor
public class WarehousePallet extends PersistentEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=60) private String code;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="area_id") private WarehouseArea area;
    @Column(nullable=false,length=10) private String lane;
    @Column(name="position_no",nullable=false) private int position;
    @Column(length=2000) private String notes;
    @Version private long version;
    public void setUpdatedAtForInventory(java.time.Instant value){updatedAt=value;}
    @OneToMany(mappedBy="pallet",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("id ASC") private List<StockItem> items=new ArrayList<>();

    @Entity(name="WarehouseStockItem") @Table(name="warehouse_stock_item") @Getter @Setter @NoArgsConstructor
    public static class StockItem {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
        @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="pallet_id") private WarehousePallet pallet;
        @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="product_id") private Product product;
        @Column(name="product_name",nullable=false,length=200) private String productName;
        @Column(name="material_code",length=100) private String materialCode;
        @Column(name="raw_material_batch",length=100) private String rawMaterialBatch;
        @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="photo_id") private WarehousePhoto photo;
        @Column(length=100) private String batch;
        @Column(length=20) private String unit;
        private Long quantity;
        private Integer cartons;
    }
}
