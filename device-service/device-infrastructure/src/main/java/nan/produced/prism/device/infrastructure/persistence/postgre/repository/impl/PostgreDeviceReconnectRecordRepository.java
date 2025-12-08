package nan.produced.prism.device.infrastructure.persistence.postgre.repository.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.device.application.dto.record.DeviceReconnectRecord;
import nan.produced.prism.device.application.port.outbound.repository.DeviceReconnectRecordRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class PostgreDeviceReconnectRecordRepository implements DeviceReconnectRecordRepository {

    private static final String INSERT_SQL = """
            INSERT INTO device_reconnect_record
                   (device_id, start_online_time, last_report_time, reconnect_time, reconnect_ip, reconnect_source, create_time)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void saveReconnectRecord(DeviceReconnectRecord deviceReconnectRecord) {
        if (deviceReconnectRecord == null) return;

        jdbcTemplate.update(INSERT_SQL,
                deviceReconnectRecord.getDeviceId(),
                deviceReconnectRecord.getStartOnlineTime(),
                deviceReconnectRecord.getLastReportTime(),
                deviceReconnectRecord.getReconnectTime(),
                deviceReconnectRecord.getReconnectIp(),
                deviceReconnectRecord.getReconnectSource(),
                LocalDateTime.now());
    }
}
