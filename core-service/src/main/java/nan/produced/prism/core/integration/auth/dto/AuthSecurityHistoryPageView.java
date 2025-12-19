package nan.produced.prism.core.integration.auth.dto;

import java.util.List;

public record AuthSecurityHistoryPageView(
    List<AuthSecurityEventView> items,
    int page,
    int size,
    long total
) {
}

