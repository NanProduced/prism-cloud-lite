package nan.produced.prism.core.export.api.dto;

import lombok.Builder;

@Builder
public record ExportFieldDefinition(
        String key,
        String headerI18nKey,
        ExportValueType valueType
) {
}

