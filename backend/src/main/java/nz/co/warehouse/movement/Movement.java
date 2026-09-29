package nz.co.warehouse.movement;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;
import nz.co.warehouse.person.Person;
import java.time.Instant;
import java.util.*;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="movement")
public class Movement extends PersistentEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="record_no",nullable=false,length=20) private String recordNo;
    @Column(name="idempotency_key",nullable=false,length=36) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private MovementEnums.Direction direction;
    @Column(name="movement_time",nullable=false) private Instant movementTime;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sender_person_id") private Person senderPerson;
    @Column(name="sender_name_snapshot",nullable=false,length=100) private String senderNameSnapshot;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="receiver_person_id") private Person receiverPerson;
    @Column(name="receiver_name_snapshot",nullable=false,length=100) private String receiverNameSnapshot;
    @Column(name="manufacture_lot",length=100) private String manufactureLot;
    @Column(name="total_cartons",nullable=false) private int totalCartons;
    @Column(length=2000) private String remarks;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private MovementEnums.Status status=MovementEnums.Status.ACTIVE;
    @Column(name="void_reason",length=500) private String voidReason;
    @Column(name="voided_at") private Instant voidedAt;
    @OneToMany(mappedBy="movement",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("sortOrder asc") private List<MovementItem> items=new ArrayList<>();
    @OneToMany(mappedBy="movement",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("createdAt asc") private Set<MovementPhoto> photos=new LinkedHashSet<>();
    public void addItem(MovementItem item){items.add(item);item.setMovement(this);}
}
