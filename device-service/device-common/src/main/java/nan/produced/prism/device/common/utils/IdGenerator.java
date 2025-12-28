package nan.produced.prism.device.common.utils;

import nan.produced.prism.device.common.exception.tech.TechErrorCode;
import nan.produced.prism.device.common.exception.tech.TechException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量级 ID 生成器（基于时间戳 + 序列号）。
 *
 * <p>说明：当前服务部分业务表使用 BIGINT 作为主键（例如设备账号表的 device_id），
 * 且不依赖数据库自增/序列，因此需要在保存前由应用侧生成唯一 ID。</p>
 */
public final class IdGenerator {

    private static final int SEQUENCE_BITS = 12;
    private static final int MAX_SEQUENCE = (1 << SEQUENCE_BITS) - 1;

    private static final AtomicLong LAST_TIMESTAMP = new AtomicLong(-1L);
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    private IdGenerator() {
        throw new TechException(TechErrorCode.INSTANTIATION_IS_PROHIBITED);
    }

    public static long nextId() {
        while (true) {
            long now = System.currentTimeMillis();
            long last = LAST_TIMESTAMP.get();

            if (now < last) {
                now = last;
            }

            if (now == last) {
                int seq = SEQUENCE.incrementAndGet();
                if (seq <= MAX_SEQUENCE) {
                    return (now << SEQUENCE_BITS) | (seq & MAX_SEQUENCE);
                }
                waitNextMillis(now);
                continue;
            }

            if (LAST_TIMESTAMP.compareAndSet(last, now)) {
                SEQUENCE.set(0);
                return (now << SEQUENCE_BITS);
            }
        }
    }

    private static void waitNextMillis(long currentMillis) {
        long now = System.currentTimeMillis();
        while (now <= currentMillis) {
            now = System.currentTimeMillis();
        }
        LAST_TIMESTAMP.set(now);
        SEQUENCE.set(0);
    }
}

