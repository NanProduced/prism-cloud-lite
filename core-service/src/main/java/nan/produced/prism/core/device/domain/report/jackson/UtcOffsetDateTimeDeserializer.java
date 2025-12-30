package nan.produced.prism.core.device.domain.report.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Deserializes device reported "UTC time" fields.
 *
 * <p>Device reports may send UTC timestamps without an explicit timezone/offset
 * (e.g. {@code yyyy-MM-dd HH:mm:ss}). For utc* fields we interpret such values
 * as UTC and materialize an {@link OffsetDateTime} with {@link ZoneOffset#UTC}.
 */
public class UtcOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    private static final DateTimeFormatter[] LOCAL_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
    };

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String raw = parser.getValueAsString();
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }

        // Epoch millis/seconds
        if (s.matches("^-?\\d+$")) {
            try {
                long epoch = Long.parseLong(s);
                if (s.length() <= 10) {
                    return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC);
                }
                return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
            } catch (Exception ignore) {
            }
        }

        // ISO-8601 with offset/zone
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception ignore) {
        }

        // Common patterns without offset -> treat as UTC.
        for (DateTimeFormatter formatter : LOCAL_FORMATTERS) {
            try {
                LocalDateTime local = LocalDateTime.parse(s, formatter);
                return local.atOffset(ZoneOffset.UTC);
            } catch (Exception ignore) {
            }
        }

        throw context.weirdStringException(s, OffsetDateTime.class, "Unsupported UTC timestamp format");
    }
}
