package nz.co.warehouse.inventory;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class WarehouseUpgradeTest {
    @Test void aisleDataUpgradePreservesExistingPalletsAndHandlesRenamedDefault() throws Exception {
        String sql;
        try(var resource=getClass().getResourceAsStream("/db/migration/V18__warehouse_flexible_materials.sql")){
            sql=new String(resource.readAllBytes(),StandardCharsets.UTF_8);
        }
        // Execute the migration's actual data-upgrade statements against both previous layouts.
        String upgrade=sql.substring(sql.indexOf("UPDATE warehouse_area"));
        for(String previousName:new String[]{"过道 1","收货区过道"}){
            try(var connection=DriverManager.getConnection("jdbc:h2:mem:upgrade-"+UUID.randomUUID()+";MODE=MySQL");var statement=connection.createStatement()){
                statement.execute("CREATE TABLE warehouse_area(id BIGINT AUTO_INCREMENT PRIMARY KEY,kind VARCHAR(10),name VARCHAR(60) UNIQUE,lanes INT,version BIGINT DEFAULT 0,rack_row VARCHAR(1),created_at TIMESTAMP,updated_at TIMESTAMP)");
                statement.execute("CREATE TABLE old_pallet(id BIGINT PRIMARY KEY,area_id BIGINT REFERENCES warehouse_area(id),lane VARCHAR(10),position_no INT)");
                try(var insert=connection.prepareStatement("INSERT INTO warehouse_area(kind,name,lanes) VALUES ('AISLE',?,2)")){insert.setString(1,previousName);insert.executeUpdate();}
                statement.execute("INSERT INTO old_pallet VALUES(1,1,'LEFT_1',3)");
                for(String part:upgrade.split(";"))if(!part.isBlank())statement.execute(part);
                try(var rows=statement.executeQuery("SELECT a.name,p.lane,p.position_no FROM old_pallet p JOIN warehouse_area a ON a.id=p.area_id")){
                    assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo(previousName);assertThat(rows.getString(2)).isEqualTo("LEFT_1");assertThat(rows.getInt(3)).isEqualTo(3);
                }
                try(var rows=statement.executeQuery("SELECT COUNT(*) FROM warehouse_area WHERE rack_row='A' AND lanes=4")){rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
                try(var rows=statement.executeQuery("SELECT COUNT(*) FROM warehouse_area")){rows.next();assertThat(rows.getInt(1)).isEqualTo(previousName.equals("过道 1")?1:2);}
            }
        }
    }
}
