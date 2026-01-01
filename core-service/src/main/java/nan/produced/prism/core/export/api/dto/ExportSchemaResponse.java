package nan.produced.prism.core.export.api.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record ExportSchemaResponse(
        String exportType,
        List<String> allowedFormats,
        List<String> defaultFields,
        List<ExportFieldDefinition> fields
) {
}

