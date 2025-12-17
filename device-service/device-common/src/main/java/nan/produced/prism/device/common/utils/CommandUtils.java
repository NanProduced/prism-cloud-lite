package nan.produced.prism.device.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 指令工具类
 *
 * @author Nan
 */
@Slf4j
public class CommandUtils {

    /**
     * 原子递增计数器
     * 初始值为0，第一次调用返回1
     */
    private static final AtomicInteger currentId = new AtomicInteger(0);

    /**
     * 重置阈值：当ID达到这个值时重置为0
     * 使用Integer.MAX_VALUE - 1000 留出安全边界
     */
    private static final int RESET_THRESHOLD = Integer.MAX_VALUE - 1000;

    /**
     * 生成设备服务与设备交互的指令Id - 仅要求同一设备一定时间内Id不重复即可（不落库）
     * @return 设备服务与设备交互的指令Id
     */
    public static Integer generateQueueId() {
        int nextId = currentId.incrementAndGet();

        // 检查是否需要重置
        if (nextId >= RESET_THRESHOLD) {
            // 使用CAS操作确保线程安全的重置
            if (currentId.compareAndSet(nextId, 1)) {
                log.info("CommandIdGenerator - 指令ID生成器已重置，从1重新开始。上一个ID: {}", nextId);
                return 1;
            } else {
                // 如果CAS失败，说明其他线程已经重置，重新获取
                return currentId.incrementAndGet();
            }
        }

        return nextId;
    }

    public static void reset() {
        int oldValue = currentId.getAndSet(0);
        log.warn("CommandIdGenerator - ID生成器手动重置。原值: {}", oldValue);
    }
}
