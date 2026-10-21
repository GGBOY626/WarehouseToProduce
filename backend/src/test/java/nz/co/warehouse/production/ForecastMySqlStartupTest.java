package nz.co.warehouse.production;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import nz.co.warehouse.product.*;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="FORECAST_MYSQL_TEST",matches="true")
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:mysql://127.0.0.1:33321/forecast_boot?useSSL=false&allowPublicKeyRetrieval=true",
    "spring.datasource.username=root","spring.datasource.password=",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate"
})
class ForecastMySqlStartupTest {
    @Autowired ProductService products;
    @Autowired ProductionTaskService tasks;
    @Test void springBootStartsWithV21AndDefaultsForecastToOneToOne(){
        var raw=products.create(new ProductDtos.Request("QA material","RAW",null,ProductUsage.WAREHOUSE_TO_PRODUCTION,400,"个"));
        var output=products.create(new ProductDtos.Request("QA finished","FIN",null,ProductUsage.PRODUCTION_TO_WAREHOUSE,null,"个",false,List.of(),List.of(),List.of(new BoxPacking(22,30,40))));
        assertThat(products.search("FIN",false,ProductUsage.PRODUCTION_TO_WAREHOUSE).getFirst().boxPackings().getFirst().getCartonsPerPallet()).isEqualTo(40);
        var task=tasks.create(new ProductionTaskService.SaveRequest(null,null,LocalDate.now(),"QA",null,
                List.of(new ProductionTaskService.TargetRequest(raw.id(),132000)),output.id(),null,"QA forecast",null,
                22,30,ProductionTask.OutputQuantityBasis.UNIT,raw.id(),40,2L,1L));
        var saved=tasks.detail(task.id());
        assertThat(saved.output().cartonsPerPallet()).isEqualTo(40);
        assertThat(saved.forecast().status()).isEqualTo("READY");
        assertThat(saved.forecast().expected().cartons()).isEqualTo("0");
    }
}
