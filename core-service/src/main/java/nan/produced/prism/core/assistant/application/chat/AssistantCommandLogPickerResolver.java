package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceCommandLogRepositoryJpa;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceRepositoryJpa;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Heuristic resolver: when the user asks "why my command didn't execute" without specifying which command,
 * return a candidate list for frontend interactive selection.
 */
@Component
@RequiredArgsConstructor
public class AssistantCommandLogPickerResolver {

    private static final int MAX_CANDIDATES = 12;
    private static final Pattern HAS_ID_PATTERN = Pattern.compile("\\b\\d{3,}\\b");
    private static final Pattern HAS_UUID_PATTERN = Pattern.compile("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b",
            Pattern.CASE_INSENSITIVE);

    private final DeviceCommandLogRepositoryJpa commandLogRepositoryJpa;
    private final DeviceRepositoryJpa deviceRepositoryJpa;

    public record PickCommandItem(String commandLogId,
                                  String deviceName,
                                  String actionType,
                                  String status,
                                  String createdAt) {
    }

    public record PickPayload(String title, List<PickCommandItem> items) {
    }

    public PickPayload resolve(UUID userId, String userText) {
        if (userId == null || !StringUtils.hasText(userText)) {
            return null;
        }
        String text = userText.trim();
        if (!shouldTrigger(text)) {
            return null;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = now.minusDays(7);

        Specification<DeviceCommandLog> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("userId"), userId),
                cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                cb.lessThanOrEqualTo(root.get("createdAt"), now)
        );

        var pageable = PageRequest.of(
                0,
                MAX_CANDIDATES,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        List<DeviceCommandLog> logs = commandLogRepositoryJpa.findAll(spec, pageable).getContent();
        if (logs == null || logs.isEmpty()) {
            return null;
        }

        Set<Long> deviceIds = logs.stream()
                .map(DeviceCommandLog::getDeviceId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        Map<Long, String> names = deviceIds.isEmpty()
                ? Map.of()
                : deviceRepositoryJpa.findByUserIdAndDeviceIdIn(userId, deviceIds).stream()
                .filter(d -> d != null && d.getDeviceId() != null)
                .collect(Collectors.toMap(
                        DeviceEntity::getDeviceId,
                        d -> StringUtils.hasText(d.getDeviceName()) ? d.getDeviceName().trim() : ("Device " + d.getDeviceId()),
                        (a, b) -> a
                ));

        List<PickCommandItem> items = new ArrayList<>();
        for (DeviceCommandLog log : logs) {
            if (log == null || log.getId() == null || log.getDeviceId() == null) {
                continue;
            }
            String deviceName = names.getOrDefault(log.getDeviceId(), "Device " + log.getDeviceId());
            items.add(new PickCommandItem(
                    String.valueOf(log.getId()),
                    deviceName,
                    log.getActionType() != null ? log.getActionType().name() : null,
                    log.getStatus() != null ? log.getStatus().name() : null,
                    log.getCreatedAt() != null ? log.getCreatedAt().toString() : null
            ));
        }

        if (items.isEmpty()) {
            return null;
        }

        return new PickPayload("请选择要排查的指令", List.copyOf(items));
    }

    private static boolean shouldTrigger(String userText) {
        String lowered = userText.toLowerCase(Locale.ROOT);
        if (HAS_UUID_PATTERN.matcher(lowered).find()) {
            return false;
        }
        if (HAS_ID_PATTERN.matcher(lowered).find()) {
            return false;
        }
        boolean mentionsCommand = lowered.contains("指令")
                || lowered.contains("命令")
                || lowered.contains("command");
        if (!mentionsCommand) {
            return false;
        }
        boolean mentionsProblem = lowered.contains("没执行")
                || lowered.contains("不执行")
                || lowered.contains("没生效")
                || lowered.contains("失败")
                || lowered.contains("待下发")
                || lowered.contains("pending")
                || lowered.contains("为什么")
                || lowered.contains("怎么")
                || lowered.contains("排查")
                || lowered.contains("诊断")
                || lowered.contains("status");
        if (!mentionsProblem) {
            return false;
        }
        return !lowered.contains("排程") && !lowered.contains("schedule");
    }
}
