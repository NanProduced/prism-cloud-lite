package nan.produced.prism.core.device.application.port.inbound;

/**
 * 自定义字段选项输入规格（用于创建/更新字段定义）。
 */
public record DeviceCustomFieldOptionSpec(
        String optionKey,
        String displayName,
        String description,
        Integer sequence,
        Boolean active,
        String color) {
}

