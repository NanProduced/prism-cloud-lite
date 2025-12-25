package nan.produced.prism.core.integration.auth.dto;

import java.util.List;

public record AuthSubscriptionHistoryPageView(
    List<AuthSubscriptionEventView> items,
    int page,
    int size,
    long total
) {
}

