ALTER TABLE production_task DROP CHECK chk_production_task_status;
ALTER TABLE production_task
    ADD CONSTRAINT chk_production_task_status
    CHECK (status IN ('PREVIEW','PREPARING','IN_PROGRESS','COMPLETED','CANCELLED'));
