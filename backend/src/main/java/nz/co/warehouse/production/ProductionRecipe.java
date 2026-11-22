package nz.co.warehouse.production;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nz.co.warehouse.common.PersistentEntity;
import nz.co.warehouse.product.Product;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "production_recipe", uniqueConstraints = @UniqueConstraint(columnNames = {"output_product_id", "name"}))
public class ProductionRecipe extends PersistentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "output_product_id", nullable = false) private Product outputProduct;
    @Column(nullable = false, length = 100) private String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "primary_material_product_id", nullable = false) private Product primaryMaterial;
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC") private List<ProductionRecipeItem> items = new ArrayList<>();
}
