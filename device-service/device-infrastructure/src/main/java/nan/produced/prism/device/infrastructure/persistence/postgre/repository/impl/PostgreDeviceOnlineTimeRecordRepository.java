package nan.produced.prism.device.infrastructure.persistence.postgre.repository.impl;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.device.application.dto.record.DeviceOnlineTimeRecord;
import nan.produced.prism.device.application.port.outbound.repository.DeviceOnlineTimeRecordRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class PostgreDeviceOnlineTimeRecordRepository implements DeviceOnlineTimeRecordRepository {

    private static final String INSERT_SQL = """
            INSERT INTO device_online_time_record
                   (device_id, online_time, offline_time, duration, create_time)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void saveDeviceOnlineTimeRecord(DeviceOnlineTimeRecord deviceOnlineTimeRecord) {
        if (deviceOnlineTimeRecord == null) return;

        jdbcTemplate.update(INSERT_SQL,
                deviceOnlineTimeRecord.getDeviceId(),
                Timestamp.valueOf(deviceOnlineTimeRecord.getStartTime()),
                Timestamp.valueOf(deviceOnlineTimeRecord.getEndTime()),
                Math.max(Duration.between(deviceOnlineTimeRecord.getStartTime(), deviceOnlineTimeRecord.getEndTime()).getSeconds(), 0),
                Timestamp.valueOf(LocalDateTime.now()));
    }

}
