package nz.co.warehouse.inventory;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;
import nz.co.warehouse.product.Product;
import java.util.*;

@Entity @Table(name="third_party_stock",uniqueConstraints=@UniqueConstraint(columnNames={"warehouse_id","warehouse_number"}))
@Getter @Setter @NoArgsConstructor
public class ThirdPartyStock extends PersistentEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id") private ThirdPartyWarehouse warehouse;
    @Column(name="warehouse_number",nullable=false,length=100) private String warehouseNumber;
    @Column(length=2000) private String notes;
    @Version private long version;
    public void touch(){updatedAt=java.time.Instant.now();}
    @OneToMany(mappedBy="stock",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("id ASC") private List<Item> items=new ArrayList<>();

    @Entity(name="ThirdPartyStockItem") @Table(name="third_party_stock_item")
    @Getter @Setter @NoArgsConstructor
    public static class Item {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
        @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="stock_id") private ThirdPartyStock stock;
        @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="product_id") private Product product;
        @Column(name="product_name",nullable=false,length=200) private String productName;
        @Column(name="material_code",length=100) private String materialCode;
        @Column(name="production_number",length=100) private String productionNumber;
        @Column(name="material_batch",length=100) private String materialBatch;
        @Column(length=20) private String unit;
        private Long quantity;
        private Integer cartons;
    }
}
