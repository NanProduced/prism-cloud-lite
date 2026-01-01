package nan.produced.prism.core.search.api.dto;

import lombok.Builder;

@Builder
public record DeviceSearchResult(
        Long id,
        String name,
        String serialNo,
        String ip,
        String status,
        String model
) {
}

