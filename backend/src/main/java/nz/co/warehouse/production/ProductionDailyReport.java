package nz.co.warehouse.production;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nz.co.warehouse.common.PersistentEntity;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="production_daily_report",uniqueConstraints=@UniqueConstraint(columnNames={"production_task_id","report_date"}))
public class ProductionDailyReport extends PersistentEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="production_task_id",nullable=false)
    @org.hibernate.annotations.OnDelete(action=org.hibernate.annotations.OnDeleteAction.CASCADE) private ProductionTask task;
    @Column(name="report_date",nullable=false) private LocalDate reportDate;
    @Column(name="group_a_cartons",nullable=false) private long groupACartons;
    @Column(name="group_b_cartons",nullable=false) private long groupBCartons;
}
