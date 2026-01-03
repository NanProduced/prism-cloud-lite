package nan.produced.prism.core.integration.auth.dto;

import java.util.List;

public record AuthInternalUserSearchPageView(
    List<AuthInternalUserSearchItem> items,
    int page,
    int size,
    long total
) {
}

