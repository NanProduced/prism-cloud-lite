package nan.produced.prism.core.export.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Data;

@Data
public class CreateExportRequest {

    /**
     * ExportType 字符串：DEVICE_LOGS / COMMAND_LOGS / DEVICE_ONLINE_SESSIONS / PROGRAM_PLAY_SESSIONS / MEDIA_PLAY_SESSIONS
     */
    private String exportType;

    /**
     * ExportFormat 字符串：CSV / XLSX / JSON / PARQUET
     */
    private String format;

    /**
     * BCP 47 Locale，例如：zh-CN / en-US
     */
    private String locale;

    /**
     * IANA 时区或 offset，例如：Asia/Shanghai / UTC / +08:00
     */
    private String timeZone;

    /**
     * 选择导出的字段 key 列表（按顺序输出）。
     */
    private List<String> fields;

    /**
     * 导出过滤条件（按 exportType 定义）。
     */
    private JsonNode filters;
}

