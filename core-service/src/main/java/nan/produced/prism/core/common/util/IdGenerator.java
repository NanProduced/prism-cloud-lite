package nan.produced.prism.core.common.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;

/**
 * 轻量级 ID 生成器（基于时间戳 + 序列号）。
 *
 * <p>说明：当前项目多处使用 Long 作为业务主键（设备标签/自定义字段等），
 * 这里提供一个不依赖数据库序列的进程内唯一 ID 方案，便于在后续接入雪花算法或统一 ID 服务前过渡。</p>
 */
public final class IdGenerator {

    private static final int SEQUENCE_BITS = 12;
    private static final int MAX_SEQUENCE = (1 << SEQUENCE_BITS) - 1;

    private static final AtomicLong LAST_TIMESTAMP = new AtomicLong(-1L);
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    private IdGenerator() {
        throw new InfraException(ErrorCode.INSTANTIATION_IS_PROHIBITED);
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

