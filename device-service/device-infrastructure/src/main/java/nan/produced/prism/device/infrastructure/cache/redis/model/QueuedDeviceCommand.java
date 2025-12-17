package nan.produced.prism.device.infrastructure.cache.redis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis 指令详情存储模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueuedDeviceCommand {

    private String commandId;

    private String authorUrl;

    private Integer karma;

    private String contentRaw;

    private String dedupeKey;

    private Long createdAtMs;
}

