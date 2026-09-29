package nz.co.warehouse.photo;

import nz.co.warehouse.movement.MovementPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MovementPhotoRepository extends JpaRepository<MovementPhoto,Long> {
    long countByMovementId(long movementId);
    Optional<MovementPhoto> findByIdAndMovementId(long id,long movementId);
}
