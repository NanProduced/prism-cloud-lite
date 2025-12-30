package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import nan.produced.prism.core.user.api.StorageSourceType;

@Schema(description = "存储对账汇总（按 sourceType）")
public record UserStorageLedgerSourceView(
        @Schema(description = "来源类型")
        StorageSourceType sourceType,
        @Schema(description = "该来源占用总字节数")
        long totalBytes,
        @Schema(description = "该来源文件总数")
        int totalCount,
        @Schema(description = "该来源下按 fileType 拆分的条目")
        List<UserStorageLedgerItemView> items
) {
}

