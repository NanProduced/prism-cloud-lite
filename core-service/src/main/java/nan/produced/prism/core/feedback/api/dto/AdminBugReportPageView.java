package nan.produced.prism.core.feedback.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "管理端 bug 分页")
public record AdminBugReportPageView(
        List<AdminBugReportListItemView> items,
        int page,
        int size,
        long total
) {
}

