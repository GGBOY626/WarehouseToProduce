package nz.co.warehouse.product;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductArchiveTest {
    @Test void deletingAReferencedProductArchivesItInsteadOfRemovingItsHistory() {
        ProductRepository repository=mock(ProductRepository.class);
        ProductService service=new ProductService(repository);
        Product product=new Product();
        product.setId(12L);
        product.setName("Finished");
        product.setMaterialCode("SKU-1");
        when(repository.findById(12L)).thenReturn(Optional.of(product));
        when(repository.countMovementItems(12L)).thenReturn(1L);

        service.delete(12L);

        assertThat(product.isActive()).isFalse();
        verify(repository,never()).delete(product);
    }
}
