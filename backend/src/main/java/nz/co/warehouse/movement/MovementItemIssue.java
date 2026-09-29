package nz.co.warehouse.movement;

import jakarta.persistence.*;
import lombok.*;
import nz.co.warehouse.common.PersistentEntity;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="movement_item_issue")
public class MovementItemIssue extends PersistentEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="movement_item_id") private MovementItem movementItem;
    @Enumerated(EnumType.STRING) @Column(name="issue_type",nullable=false,length=40) private MovementEnums.IssueType issueType;
    @Column(length=1000) private String description;
}
