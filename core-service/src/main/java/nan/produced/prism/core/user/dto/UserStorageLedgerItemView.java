package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import nan.produced.prism.core.user.api.StorageFileType;

@Schema(description = "存储对账条目（按 fileType）")
public record UserStorageLedgerItemView(
        @Schema(description = "文件类型")
        StorageFileType fileType,
        @Schema(description = "文件数量", example = "10")
        int fileCount,
        @Schema(description = "占用字节数", example = "1024")
        long totalBytes,
        @Schema(description = "该项最后更新时间（UTC）", nullable = true)
        Instant updatedAt
) {
}

