package nz.co.warehouse.movement;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface MovementRepository extends JpaRepository<Movement,Long> {
    Optional<Movement> findByIdempotencyKey(String key);

    @EntityGraph(attributePaths={"senderPerson","receiverPerson","items","items.product"})
    @Query("select distinct m from Movement m where m.id=:id")
    Optional<Movement> findDetailById(@Param("id") long id);

    @EntityGraph(attributePaths={"senderPerson","receiverPerson","items","items.product"})
    @Query("select distinct m from Movement m where m.movementTime>=:from and m.movementTime<:to and m.status=:status order by m.movementTime asc")
    java.util.List<Movement> findReportRows(@Param("from") Instant from,@Param("to") Instant to,@Param("status") MovementEnums.Status status);

    @Query(value="""
        select distinct m from Movement m left join m.items i
        where m.movementTime >= :from and m.movementTime < :to
          and (:direction is null or m.direction = :direction)
          and (:status is null or m.status = :status)
          and (:missingPhoto is null or (:missingPhoto=true and not exists(select p.id from MovementPhoto p where p.movement=m)) or (:missingPhoto=false and exists(select p.id from MovementPhoto p where p.movement=m)))
          and (:hasIssue is null or (:hasIssue=true and exists(select iss.id from MovementItemIssue iss where iss.movementItem.movement=m)) or (:hasIssue=false and not exists(select iss.id from MovementItemIssue iss where iss.movementItem.movement=m)))
          and (:q='' or lower(m.recordNo) like lower(concat('%',:q,'%')) or lower(coalesce(m.manufactureLot,'')) like lower(concat('%',:q,'%')) or lower(i.productNameSnapshot) like lower(concat('%',:q,'%')) or lower(coalesce(i.skuSnapshot,'')) like lower(concat('%',:q,'%')) or lower(i.batchNo) like lower(concat('%',:q,'%')))
        """, countQuery="""
        select count(distinct m.id) from Movement m left join m.items i
        where m.movementTime >= :from and m.movementTime < :to
          and (:direction is null or m.direction = :direction) and (:status is null or m.status = :status)
          and (:missingPhoto is null or (:missingPhoto=true and not exists(select p.id from MovementPhoto p where p.movement=m)) or (:missingPhoto=false and exists(select p.id from MovementPhoto p where p.movement=m)))
          and (:hasIssue is null or (:hasIssue=true and exists(select iss.id from MovementItemIssue iss where iss.movementItem.movement=m)) or (:hasIssue=false and not exists(select iss.id from MovementItemIssue iss where iss.movementItem.movement=m)))
          and (:q='' or lower(m.recordNo) like lower(concat('%',:q,'%')) or lower(coalesce(m.manufactureLot,'')) like lower(concat('%',:q,'%')) or lower(i.productNameSnapshot) like lower(concat('%',:q,'%')) or lower(coalesce(i.skuSnapshot,'')) like lower(concat('%',:q,'%')) or lower(i.batchNo) like lower(concat('%',:q,'%')))
        """)
    Page<Movement> search(@Param("from") Instant from,@Param("to") Instant to,@Param("direction") MovementEnums.Direction direction,
                          @Param("status") MovementEnums.Status status,@Param("missingPhoto") Boolean missingPhoto,
                          @Param("hasIssue") Boolean hasIssue,@Param("q") String q, Pageable pageable);
}
