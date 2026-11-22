package nz.co.warehouse.production;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nz.co.warehouse.product.Product;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "production_recipe_item", uniqueConstraints = @UniqueConstraint(columnNames = {"recipe_id", "product_id"}))
public class ProductionRecipeItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recipe_id", nullable = false) private ProductionRecipe recipe;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false) private Product product;
    @Column(name = "quantity_per_output", nullable = false, precision = 18, scale = 6) private BigDecimal quantityPerOutput;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
}
