package nz.co.warehouse.movement;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class BoxPackingUpgradeTest {
    @Test void migrationPreservesLegacyTotalsAndLeavesFactorsUnknown() throws Exception {
        String sql;
        try(var resource=getClass().getResourceAsStream("/db/migration/V19__product_box_packing.sql")){sql=new String(resource.readAllBytes(),StandardCharsets.UTF_8);}
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:box-upgrade;MODE=MySQL");var statement=connection.createStatement()){
            statement.execute("CREATE TABLE product(id BIGINT PRIMARY KEY,default_units_per_carton INT)");
            statement.execute("CREATE TABLE movement_item(id BIGINT PRIMARY KEY,total_units BIGINT)");
            statement.execute("INSERT INTO product VALUES(1,840)");statement.execute("INSERT INTO movement_item VALUES(1,2040)");
            for(String part:sql.split(";"))if(!part.isBlank())statement.execute(part);
            try(var rows=statement.executeQuery("SELECT total_units,boxes_per_carton_snapshot,units_per_box_snapshot FROM movement_item")){
                rows.next();assertThat(rows.getLong(1)).isEqualTo(2040);assertThat(rows.getObject(2)).isNull();assertThat(rows.getObject(3)).isNull();
            }
            try(var rows=statement.executeQuery("SELECT default_units_per_carton FROM product")){rows.next();assertThat(rows.getInt(1)).isEqualTo(840);}
            statement.execute("INSERT INTO product_box_packing VALUES(1,28,30)");
        }
    }
}
