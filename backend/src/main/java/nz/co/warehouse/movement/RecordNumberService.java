package nz.co.warehouse.movement;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;

@Service @RequiredArgsConstructor
public class RecordNumberService {
    private final JdbcTemplate jdbc;
    @Value("${app.business-zone:Pacific/Auckland}") private String zone;

    public String next(Instant movementTime) {
        LocalDate date=movementTime.atZone(ZoneId.of(zone)).toLocalDate();
        jdbc.update("INSERT INTO movement_daily_sequence(sequence_date,next_value) VALUES (?,1) ON DUPLICATE KEY UPDATE sequence_date=sequence_date",date);
        Integer value=jdbc.queryForObject("SELECT next_value FROM movement_daily_sequence WHERE sequence_date=? FOR UPDATE",Integer.class,date);
        jdbc.update("UPDATE movement_daily_sequence SET next_value=next_value+1 WHERE sequence_date=?",date);
        return "TR-"+date.format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format("%03d",value);
    }
}
