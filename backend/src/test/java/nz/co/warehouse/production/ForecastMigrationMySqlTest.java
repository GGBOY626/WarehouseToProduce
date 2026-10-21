package nz.co.warehouse.production;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

/** Opt-in, disposable LOCAL instance only. The test never cleans or drops a schema. */
@EnabledIfEnvironmentVariable(named="FORECAST_MYSQL_TEST",matches="true")
class ForecastMigrationMySqlTest {
    @Test void upgradeFromV20PreservesDataAndCanResumeAfterPartialDdl() throws Exception {
        String url="jdbc:mysql://127.0.0.1:33321/forecast_upgrade?useSSL=false&allowPublicKeyRetrieval=true";
        Flyway.configure().dataSource(url,"root","").target("20").load().migrate();
        try(var connection=DriverManager.getConnection(url,"root","");var sql=connection.createStatement()){
            sql.execute("INSERT INTO product(id,name,material_code,movement_direction,created_at,updated_at) VALUES (1,'QA raw','R','WAREHOUSE_TO_PRODUCTION',NOW(),NOW()),(2,'QA finished','F','PRODUCTION_TO_WAREHOUSE',NOW(),NOW())");
            sql.execute("INSERT INTO product_box_packing VALUES (2,22,30)");
            sql.execute("INSERT INTO production_task(product_id,target_quantity,planned_date,batch_no,created_at,updated_at) VALUES (1,132000,CURRENT_DATE,'QA',NOW(),NOW())");
            // Simulate a previous interrupted attempt after the first DDL statement.
            sql.execute("ALTER TABLE product_box_packing ADD COLUMN cartons_per_pallet INT NULL");
            Flyway.configure().dataSource(url,"root","").load().migrate();
            String migration;
            try(var resource=getClass().getResourceAsStream("/db/migration/V21__task_receipt_forecast.sql")){
                migration=new String(resource.readAllBytes(),StandardCharsets.UTF_8);
            }
            for(String part:migration.split(";"))if(!part.isBlank())sql.execute(part);
            try(var rows=sql.executeQuery("SELECT target_quantity,yield_material_quantity,yield_output_quantity,output_cartons_per_pallet FROM production_task")){
                assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isEqualTo(132000);
                assertThat(rows.getObject(2)).isNull();assertThat(rows.getObject(3)).isNull();assertThat(rows.getObject(4)).isNull();
            }
            try(var rows=sql.executeQuery("SELECT boxes_per_carton,units_per_box,cartons_per_pallet FROM product_box_packing")){
                assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(22);assertThat(rows.getInt(2)).isEqualTo(30);assertThat(rows.getObject(3)).isNull();
            }
            assertThat(Flyway.configure().dataSource(url,"root","").load().info().current().getVersion().getVersion()).isEqualTo("21");
        }
    }
}
