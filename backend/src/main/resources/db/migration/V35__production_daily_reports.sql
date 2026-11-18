CREATE TABLE production_daily_report (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    production_task_id BIGINT UNSIGNED NOT NULL,
    report_date DATE NOT NULL,
    group_a_cartons BIGINT UNSIGNED NOT NULL,
    group_b_cartons BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_production_daily_report_task_date UNIQUE (production_task_id, report_date),
    CONSTRAINT fk_production_daily_report_task FOREIGN KEY (production_task_id) REFERENCES production_task(id) ON DELETE CASCADE
);
