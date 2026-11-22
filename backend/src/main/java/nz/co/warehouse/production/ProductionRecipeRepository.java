package nz.co.warehouse.production;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProductionRecipeRepository extends JpaRepository<ProductionRecipe, Long> {
    List<ProductionRecipe> findAllByOutputProductIdOrderByName(long outputProductId);
    boolean existsByOutputProductIdAndNameIgnoreCaseAndIdNot(long outputProductId, String name, long id);
    boolean existsByOutputProductIdAndNameIgnoreCase(long outputProductId, String name);
    long countByOutputProductId(long outputProductId);
    long countByPrimaryMaterialId(long productId);
    @Query("select count(i) from ProductionRecipeItem i where i.product.id = :productId")
    long countRecipeItemsByProductId(@Param("productId") long productId);
    @Query("select count(t) from ProductionTask t where t.recipe.id = :recipeId")
    long countTasksByRecipeId(@Param("recipeId") long recipeId);
}
