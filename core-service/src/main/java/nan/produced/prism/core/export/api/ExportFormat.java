package nan.produced.prism.core.export.api;

import java.util.Locale;
import org.springframework.util.StringUtils;

public enum ExportFormat {
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    JSON("json", "application/json"),
    PARQUET("parquet", "application/vnd.apache.parquet");

    private final String extension;
    private final String contentType;

    ExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static ExportFormat parseOrThrow(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("format is blank");
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return ExportFormat.valueOf(upper);
    }
}

