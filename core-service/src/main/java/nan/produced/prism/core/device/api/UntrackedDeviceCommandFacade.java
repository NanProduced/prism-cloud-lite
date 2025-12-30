package nan.produced.prism.core.device.api;

/**
 * 未落库的 device command 标记门面（跨模块暴露）。
 *
 * <p>用于 program/schedule 等模块下发 raw 指令时，在回执侧识别并静默忽略 confirm/expired。</p>
 */
public interface UntrackedDeviceCommandFacade {

    void markByCommandId(String commandId, String type);

    void markByQueue(Long deviceId, Integer queueId, String commandId, String type);
}

