package nan.produced.prism.core.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.device.domain.report.media.MediaPlayTimesReport;
import nan.produced.prism.core.device.domain.report.program.ProgramPlayTimesReport;
import nan.produced.prism.core.device.domain.report.sensor.SensorReportBase;
import nan.produced.prism.core.device.domain.report.sensor.type.HumidityReport;
import org.junit.jupiter.api.Test;

class DeviceReportTimeDeserializationTests {

    @Test
    void shouldDeserializeProgramPlayReportsWithLocalAndUtcTimes() {
        String json = """
                [
                  {
                    "programVsn": "demo.vsn",
                    "programName": "demo",
                    "programId": "lan-uuid",
                    "startLocalTime": ["2025-12-30 14:07:39"],
                    "startUtcTime": ["2025-12-30 06:07:39"],
                    "endLocalTime": ["2025-12-30 14:09:39"],
                    "endUtcTime": ["2025-12-30 06:09:39"],
                    "times": 1,
                    "singleDuration": 120,
                    "duration": 120
                  }
                ]
                """;

        List<ProgramPlayTimesReport> reports = JsonUtils.fromJson(json, new TypeReference<List<ProgramPlayTimesReport>>() {});
        assertThat(reports).hasSize(1);

        ProgramPlayTimesReport report = reports.getFirst();
        assertThat(report.getStartLocalTime()).hasSize(1);
        assertThat(report.getStartLocalTime().getFirst())
                .isEqualTo(LocalDateTime.of(2025, 12, 30, 14, 7, 39));

        assertThat(report.getEndLocalTime()).hasSize(1);
        assertThat(report.getEndLocalTime().getFirst())
                .isEqualTo(LocalDateTime.of(2025, 12, 30, 14, 9, 39));

        assertThat(report.getStartUtcTime()).hasSize(1);
        assertThat(report.getStartUtcTime().getFirst())
                .isEqualTo(OffsetDateTime.of(2025, 12, 30, 6, 7, 39, 0, ZoneOffset.UTC));

        assertThat(report.getEndUtcTime()).hasSize(1);
        assertThat(report.getEndUtcTime().getFirst())
                .isEqualTo(OffsetDateTime.of(2025, 12, 30, 6, 9, 39, 0, ZoneOffset.UTC));
    }

    @Test
    void shouldDeserializeMediaPlayReportsWithLocalAndUtcTimes() {
        String json = """
                [
                  {
                    "programName": "demo.vsn",
                    "resOriginName": "media-1",
                    "resMd5Name": "md5.mp4",
                    "pageName": "page",
                    "pageIndex": 0,
                    "regionName": "region",
                    "regionIndex": 0,
                    "startUtcTime": "2025-12-30 06:07:39",
                    "startLocalTime": "2025-12-30 14:07:39",
                    "endUtcTime": "2025-12-30 06:09:39",
                    "endLocalTime": "2025-12-30 14:09:39",
                    "duration": 120,
                    "itemType": "video"
                  }
                ]
                """;

        List<MediaPlayTimesReport> reports = JsonUtils.fromJson(json, new TypeReference<List<MediaPlayTimesReport>>() {});
        assertThat(reports).hasSize(1);

        MediaPlayTimesReport report = reports.getFirst();
        assertThat(report.getStartLocalTime())
                .isEqualTo(LocalDateTime.of(2025, 12, 30, 14, 7, 39));
        assertThat(report.getEndLocalTime())
                .isEqualTo(LocalDateTime.of(2025, 12, 30, 14, 9, 39));
        assertThat(report.getStartUtcTime())
                .isEqualTo(OffsetDateTime.of(2025, 12, 30, 6, 7, 39, 0, ZoneOffset.UTC));
        assertThat(report.getEndUtcTime())
                .isEqualTo(OffsetDateTime.of(2025, 12, 30, 6, 9, 39, 0, ZoneOffset.UTC));
    }

    @Test
    void shouldDeserializeSensorReportTimeAsLocalDateTime() {
        String json = """
                {
                  "sensorId": 1,
                  "sensorType": "humidity",
                  "date": "2025-12-30 14:07:39",
                  "sensorValue": 12.3
                }
                """;

        SensorReportBase report = JsonUtils.fromJson(json, SensorReportBase.class);
        assertThat(report).isInstanceOf(HumidityReport.class);
        assertThat(report.getReportTime())
                .isEqualTo(LocalDateTime.of(2025, 12, 30, 14, 7, 39));
    }
}

