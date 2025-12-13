package nan.produced.prism.core.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务线程池配置
 * 为 @Async 注解提供专用的线程池
 *
 * @author Nan
 */
@Slf4j
@EnableAsync
@Configuration
public class ExecutorConfiguration {

    /**
     * CPU 核心数
     */
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * 设备事件处理线程池
     * 用于异步处理 MQ 消费的设备事件
     * 处理场景：
     * - 设备状态变化
     * - 设备指令结果
     * - 设备数据上报（属性、日志、传感器、播放记录等）
     * <p>
     * 线程池大小说明：
     * - corePoolSize: 物理线程数，始终保活的线程
     * - maxPoolSize: 最大线程数，应对峰值流量
     * - queueCapacity: 等待队列大小，缓冲任务
     *
     * @return 线程池执行器
     */
    @Bean(name = "deviceEventExecutor")
    public ThreadPoolTaskExecutor deviceEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 = CPU 核心数 / 2
        // 原因：MQ 消费是 I/O 密集型，不需要太多线程
        int corePoolSize = Math.max(2, CPU_COUNT / 2);
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 = CPU 核心数 * 2
        // 原因：应对高峰期设备上报流量
        int maxPoolSize = CPU_COUNT * 2;
        executor.setMaxPoolSize(maxPoolSize);

        // 队列容量 = 2000
        // 原因：缓冲高频设备上报（每秒可能 1000+ 条消息）
        executor.setQueueCapacity(2000);

        // 线程空闲时间 = 60 秒
        // 原因：等待下一个任务到达，超时后回收多余线程
        executor.setKeepAliveSeconds(60);

        // 线程名前缀
        executor.setThreadNamePrefix("device-event-");

        // 关闭时等待所有任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 最多等待 30 秒
        executor.setAwaitTerminationSeconds(30);

        // 拒绝策略：使用调用线程执行（CallerRunsPolicy）
        // 注意：这里不能使用异常抛出的拒绝策略，否则 MQ 消息会丢失
        // CallerRunsPolicy 会让提交任务的线程去执行，保证消息处理
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        log.info("设备事件线程池初始化完成: " +
                        "corePoolSize={}, maxPoolSize={}, queueCapacity={} (CPU核心数: {})",
                corePoolSize, maxPoolSize, executor.getQueueCapacity(), CPU_COUNT);

        return executor;
    }

    /**
     * 通知推送线程池
     * 用于异步处理向用户推送通知（SSE/WebSocket）
     * 线程数可以较小，因为推送操作很快
     *
     * @return 线程池执行器
     */
    @Bean(name = "notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 = max(2, CPU/4)
        // 原因：通知推送是轻量级操作，线程数可以较小
        int corePoolSize = Math.max(2, CPU_COUNT / 4);
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 = CPU 核心数
        int maxPoolSize = CPU_COUNT;
        executor.setMaxPoolSize(maxPoolSize);

        // 队列容量 = 1000
        executor.setQueueCapacity(1000);

        // 线程空闲时间 = 120 秒
        executor.setKeepAliveSeconds(120);

        executor.setThreadNamePrefix("notification-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        log.info("通知推送线程池初始化完成: " +
                        "corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, executor.getQueueCapacity());

        return executor;
    }

    /**
     * 后台任务处理线程池
     * 用于异步处理 core-service 内部的后台任务
     * 例如：导出任务、数据同步、报表生成等
     *
     * @return 线程池执行器
     */
    @Bean(name = "backgroundTaskExecutor")
    public ThreadPoolTaskExecutor backgroundTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 = max(2, CPU/2)
        int corePoolSize = Math.max(2, CPU_COUNT / 2);
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 = CPU
        int maxPoolSize = CPU_COUNT;
        executor.setMaxPoolSize(maxPoolSize);

        // 队列容量 = 1000
        executor.setQueueCapacity(1000);

        // 线程空闲时间 = 300 秒（5分钟）
        executor.setKeepAliveSeconds(300);

        executor.setThreadNamePrefix("background-task-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        log.info("后台任务线程池初始化完成: " +
                        "corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, executor.getQueueCapacity());

        return executor;
    }
}
