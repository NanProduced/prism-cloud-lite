package nan.produced.prism.core.telemetry.infrastructure.persistence;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceReceiveCardSampleRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceReceiveCardSampleRepositoryAdapter implements DeviceReceiveCardSampleRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String SQL_INSERT = """
            INSERT INTO pc_device_receive_card_sample (
              user_id,
              device_id,
              net_port_num,
              receive_card_num,
              x,
              y,
              width,
              height,
              bit_error_rate,
              temperature,
              humidity,
              smoke,
              report_time_raw,
              server_time
            )
            VALUES (
              :userId,
              :deviceId,
              :netPortNum,
              :receiveCardNum,
              :x,
              :y,
              :width,
              :height,
              :bitErrorRate,
              :temperature,
              :humidity,
              :smoke,
              :reportTimeRaw,
              :serverTime
            )
            """;

    private static final String SQL_LIST_BASE = """
            SELECT
              net_port_num,
              receive_card_num,
              bit_error_rate,
              temperature,
              humidity,
              smoke,
              server_time
            FROM pc_device_receive_card_sample
            WHERE user_id = :userId
              AND device_id = :deviceId
              AND server_time >= :from
              AND server_time < :to
            """;

    @Override
    public int insertBatch(List<InsertRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        SqlParameterSource[] batch = rows.stream()
            .map(r -> new MapSqlParameterSource()
                .addValue("userId", r.userId())
                .addValue("deviceId", r.deviceId())
                .addValue("netPortNum", r.netPortNum())
                .addValue("receiveCardNum", r.receiveCardNum())
                .addValue("x", r.x())
                .addValue("y", r.y())
                .addValue("width", r.width())
                .addValue("height", r.height())
                .addValue("bitErrorRate", r.bitErrorRate())
                .addValue("temperature", r.temperature())
                .addValue("humidity", r.humidity())
                .addValue("smoke", r.smoke())
                .addValue("reportTimeRaw", r.reportTimeRaw())
                .addValue("serverTime", r.serverTime()))
            .toArray(SqlParameterSource[]::new);

        int[] results = jdbcTemplate.batchUpdate(SQL_INSERT, batch);
        return Arrays.stream(results).sum();
    }

    @Override
    public List<SampleRow> listSamples(UUID userId,
                                       Long deviceId,
                                       java.time.OffsetDateTime from,
                                       java.time.OffsetDateTime to,
                                       Integer netPortNum,
                                       Integer receiveCardNum,
                                       Integer limit) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");

        StringBuilder sql = new StringBuilder(SQL_LIST_BASE);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("deviceId", deviceId)
            .addValue("from", from)
            .addValue("to", to);

        if (netPortNum != null) {
            sql.append(" AND net_port_num = :netPortNum");
            params.addValue("netPortNum", netPortNum);
        }
        if (receiveCardNum != null) {
            sql.append(" AND receive_card_num = :receiveCardNum");
            params.addValue("receiveCardNum", receiveCardNum);
        }

        sql.append(" ORDER BY server_time ASC, id ASC");

        if (limit != null && limit > 0) {
            sql.append(" LIMIT :limit");
            params.addValue("limit", limit);
        }

        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new SampleRow(
            (Integer) rs.getObject("net_port_num"),
            (Integer) rs.getObject("receive_card_num"),
            (Double) rs.getObject("bit_error_rate"),
            (Integer) rs.getObject("temperature"),
            (Integer) rs.getObject("humidity"),
            (Double) rs.getObject("smoke"),
            rs.getObject("server_time", java.time.OffsetDateTime.class)
        ));
    }
}
