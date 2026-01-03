package nan.produced.prism.core.integration.auth.dto;

import java.util.List;

public record AuthAdminUserPageView(
    List<AuthAdminUserItem> items,
    int page,
    int size,
    long total
) {
}

