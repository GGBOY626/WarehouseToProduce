package nz.co.warehouse.person;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class PersonDtos {
    private PersonDtos() {}
    public record Request(@NotBlank @Size(max=100) String name, @Size(max=500) String remarks) {}
    public record Response(Long id, String name, String remarks, boolean active, Instant createdAt, Instant updatedAt) {
        static Response from(Person p) { return new Response(p.getId(), p.getName(), p.getRemarks(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt()); }
    }
}
