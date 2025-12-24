package nan.produced.prism.core.telemetry.application.job;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorTelemetryRetentionJob {

    private static final int RETENTION_DAYS = 30;

    private static final String SQL_DELETE_SENSOR_METRIC = """
            DELETE FROM pc_device_sensor_metric
            WHERE server_time < :cutoff
            """;

    private static final String SQL_DELETE_RECEIVE_CARD = """
            DELETE FROM pc_device_receive_card_sample
            WHERE server_time < :cutoff
            """;

    private static final String SQL_DELETE_GPS_POINT = """
            DELETE FROM pc_device_gps_point
            WHERE server_time < :cutoff
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 保留最近 30 天传感器数据：每日 UTC 03:40 清理一次。
     */
    @Scheduled(cron = "0 40 3 * * *", zone = "UTC")
    @Transactional
    public void cleanupExpired() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("cutoff", cutoff);

        int deletedMetrics = jdbcTemplate.update(SQL_DELETE_SENSOR_METRIC, params);
        int deletedCards = jdbcTemplate.update(SQL_DELETE_RECEIVE_CARD, params);
        int deletedGps = jdbcTemplate.update(SQL_DELETE_GPS_POINT, params);

        int total = deletedMetrics + deletedCards + deletedGps;
        if (total > 0) {
            log.info("SensorTelemetryRetentionJob - deleted metrics={}, receiveCards={}, gpsPoints={} before {}",
                deletedMetrics, deletedCards, deletedGps, cutoff);
        }
    }
}

