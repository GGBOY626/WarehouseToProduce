package nz.co.warehouse.product;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class ProductDtos {
    private ProductDtos() {}
    public record Request(@NotBlank @Size(max=200) String name,
                          @NotBlank @Size(max=100) String materialCode,
                          @Size(max=100) String materialBatch,
                          @Positive Integer defaultUnitsPerCarton,
                          @Size(max=20) String baseUnit,
                          boolean quantityUnknown) {
        public Request(String name,String materialCode,String materialBatch,Integer defaultUnitsPerCarton,String baseUnit){
            this(name,materialCode,materialBatch,defaultUnitsPerCarton,baseUnit,false);
        }
    }
    public record Response(Long id, String name, String materialCode, String materialBatch, Integer defaultUnitsPerCarton,
                           String baseUnit, boolean quantityUnknown, boolean active, boolean duplicateName, Instant createdAt, Instant updatedAt) {
        static Response from(Product p) { return from(p, false); }
        static Response from(Product p, boolean duplicateName) { return new Response(p.getId(), p.getName(), p.getMaterialCode(), p.getMaterialBatch(), p.getDefaultUnitsPerCarton(), p.getBaseUnit(), p.isQuantityUnknown(), p.isActive(), duplicateName, p.getCreatedAt(), p.getUpdatedAt()); }
    }
}
