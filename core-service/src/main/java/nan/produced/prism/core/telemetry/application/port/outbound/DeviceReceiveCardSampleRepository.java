package nan.produced.prism.core.telemetry.application.port.outbound;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface DeviceReceiveCardSampleRepository {

    record InsertRow(
        UUID userId,
        Long deviceId,
        Integer netPortNum,
        Integer receiveCardNum,
        Integer x,
        Integer y,
        Integer width,
        Integer height,
        Double bitErrorRate,
        Integer temperature,
        Integer humidity,
        Double smoke,
        String reportTimeRaw,
        OffsetDateTime serverTime
    ) {
    }

    record SampleRow(
        Integer netPortNum,
        Integer receiveCardNum,
        Double bitErrorRate,
        Integer temperature,
        Integer humidity,
        Double smoke,
        OffsetDateTime serverTime
    ) {
    }

    int insertBatch(List<InsertRow> rows);

    List<SampleRow> listSamples(
        UUID userId,
        Long deviceId,
        OffsetDateTime from,
        OffsetDateTime to,
        Integer netPortNum,
        Integer receiveCardNum,
        Integer limit);
}
