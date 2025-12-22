package nan.produced.prism.core.program.domain.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 排程允许的指令类型（Lite 阶段先内置常用指令，禁止前端任意拼装 raw payload）。
 *
 * <p>该枚举用于生成终端协议 commandSchedule 元素中的 operation(author_url/karma/content)。</p>
 */
@Getter
@Schema(description = "排程允许的指令类型")
public enum ScheduleCommandType {

    @Schema(description = "亮度调节（PUT api/brightness）")
    BRIGHTNESS("Brightness_Control", "api/brightness", 2),

    @Schema(description = "音量调节（PUT api/volume）")
    VOLUME("Volume_Control", "api/volume", 2),

    @Schema(description = "色温调节（PUT api/colortemp）")
    COLORTEMP("Colortemp_Control", "api/colortemp", 2),

    @Schema(description = "休眠（POST api/action）")
    SLEEP("Sleep", "api/action", 1),

    @Schema(description = "唤醒（POST api/action）")
    WAKEUP("Wakeup", "api/action", 1),

    @Schema(description = "重启（POST api/action）")
    REBOOT("Reboot", "api/action", 1),

    @Schema(description = "清理缓存（DELETE api/clrresunused）")
    CLEAR_CACHE("Clear_Cache", "api/clrresunused", 3),

    @Schema(description = "切换信号源（PUT api/inputmode）")
    SWITCH_SIGNAL_SOURCE("Switch_Signal_Source", "api/inputmode", 2),

    @Schema(description = "继电器控制（PUT api/relay）")
    RELAY("Relay", "api/relay", 2),

    @Schema(description = "板载继电器控制（PUT api/board_relay）")
    BOARD_RELAY("Board_Relay", "api/board_relay", 2);

    /**
     * 对应终端协议 commandSchedule.name
     */
    private final String scheduleName;

    /**
     * 对应终端协议 operation.author_url
     */
    private final String authorUrl;

    /**
     * 对应终端协议 operation.karma
     * <p>0-get, 1-post, 2-put,3-delete</p>
     */
    private final int karma;

    ScheduleCommandType(String scheduleName, String authorUrl, int karma) {
        this.scheduleName = scheduleName;
        this.authorUrl = authorUrl;
        this.karma = karma;
    }
}
