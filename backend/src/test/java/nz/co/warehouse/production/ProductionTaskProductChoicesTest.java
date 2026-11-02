package nz.co.warehouse.production;

import nz.co.warehouse.movement.MovementEnums;
import nz.co.warehouse.movement.MovementRepository;
import nz.co.warehouse.product.Product;
import nz.co.warehouse.product.ProductService;
import nz.co.warehouse.product.ProductUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionTaskProductChoicesTest {
    @Mock ProductionTaskRepository repository;
    @Mock ProductionTaskPhotoRepository photos;
    @Mock ProductionTaskPhotoService photoService;
    @Mock MovementRepository movements;
    @Mock ProductService products;
    @InjectMocks ProductionTaskService service;

    @Test void suggestsOnlyProductsFromInProgressTasksForBothDirections() {
        Product material = product(1L, ProductUsage.WAREHOUSE_TO_PRODUCTION);
        Product secondMaterial = product(2L, ProductUsage.WAREHOUSE_TO_PRODUCTION);
        Product output = product(3L, ProductUsage.PRODUCTION_TO_WAREHOUSE);
        ProductionTask first = task(output, material, secondMaterial);
        ProductionTask second = task(output, material, output);
        when(repository.search(ProductionTask.Status.IN_PROGRESS)).thenReturn(List.of(first, second));

        assertThat(service.productChoices(MovementEnums.Direction.WAREHOUSE_TO_PRODUCTION))
                .containsExactly(1L, 2L);
        assertThat(service.productChoices(MovementEnums.Direction.PRODUCTION_TO_WAREHOUSE))
                .containsExactly(3L);
        verify(repository, times(2)).search(ProductionTask.Status.IN_PROGRESS);
    }

    private Product product(long id, ProductUsage usage) {
        Product product = new Product();
        product.setId(id);
        product.setMovementDirection(usage);
        return product;
    }

    private ProductionTask task(Product output, Product... materials) {
        ProductionTask task = new ProductionTask();
        task.setOutputProduct(output);
        for (Product material : materials) {
            ProductionTaskTarget target = new ProductionTaskTarget();
            target.setProduct(material);
            task.getTargets().add(target);
        }
        return task;
    }
}
