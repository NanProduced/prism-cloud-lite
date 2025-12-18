package nan.produced.prism.device.boot.config.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.RejectedExecutionException;

@Slf4j
@EnableAsync
@EnableScheduling
@Configuration
public class ExecutorConfiguration {

    /**
     * CPU核心数 - 用于动态配置线程池大小
     */
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * 定时任务调度器（全局一个就行，多了没用）
     * 用于离线检测、TTL刷新等定时任务
     * <p>
     * 【分类】调度型 - 不宜过多线程，避免调度开销
     * 【策略】保持适度固定值，最多不超过CPU核心数
     */
    @Primary
    @Bean("deviceTaskScheduler")
    public TaskScheduler deviceTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        // 核心线程数 - 调度型任务，避免过度调度，最多不超过CPU核心数
        int poolSize = Math.clamp(CPU_COUNT, 2, 10);
        scheduler.setPoolSize(poolSize);

        // 线程名前缀
        scheduler.setThreadNamePrefix("device-scheduler-");

        // 设置线程为守护线程
        scheduler.setDaemon(true);

        // 设置等待所有任务完成后关闭线程池
        scheduler.setWaitForTasksToCompleteOnShutdown(true);

        // 设置等待时间
        scheduler.setAwaitTerminationSeconds(30);

        // 拒绝策略：由调用者线程执行（定时任务是关键任务，保证执行）
        // 增强监控：记录详细的拒绝信息
        scheduler.setRejectedExecutionHandler((r, executor) -> {
            log.warn("ThreadPool - 定时任务被拒绝，使用调用者线程执行: pool=device-scheduler, " +
                            "activeCount={}, poolSize={}, taskClass={}",
                    executor.getActiveCount(), executor.getPoolSize(),
                    r.getClass().getSimpleName());
            // 使用调用者线程执行，确保定时任务不丢失
            r.run();
        });

        scheduler.initialize();

        log.info("ThreadPool - 定时任务线程池初始化完成: poolSize={} (CPU核心数: {}), 支持延迟启动配置",
                scheduler.getPoolSize(), CPU_COUNT);

        return scheduler;
    }

    @Bean(name = "websocketConnectionExecutor")
    public ThreadPoolTaskExecutor websocketConnectionExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 - I/O密集型：连接初始化+Redis操作
        int corePoolSize = Math.max(4, CPU_COUNT * 2);
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 - 支持连接建立高峰
        int maxPoolSize = Math.max(16, CPU_COUNT * 3);
        executor.setMaxPoolSize(maxPoolSize);

        // 队列容量 - 缓冲连接建立请求
        executor.setQueueCapacity(500);

        // 线程空闲时间 - 较长保活时间，适应连接建立的波峰波谷
        executor.setKeepAliveSeconds(300);

        // 线程命名
        executor.setThreadNamePrefix("ws-connection-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // 拒绝策略：抛出异常 + 告警（避免I/O线程卡顿）
        // 重要提示：websocketConnectionExecutor被Netty WebSocket处理器使用，调用者是I/O线程
        // 如果使用CallerRunsPolicy在I/O线程执行阻塞操作，会导致整个Netty EventLoop卡顿
        // 即使连接建立是关键任务，也不能在I/O线程执行同步阻塞操作，否则优先级反转
        executor.setRejectedExecutionHandler((task, ext) -> {
            String errorMsg = String.format(
                    "ThreadPool - WebSocket连接线程池已满（拒绝任务）: pool=ws-connection, activeCount=%d, queueSize=%d, maxPoolSize=%d, taskClass=%s, 建议检查连接建立高峰是否过载",
                    ext.getActiveCount(), ext.getQueue().size(), ext.getMaximumPoolSize(),
                    task.getClass().getSimpleName());
            log.error(errorMsg);
            // ✅ 抛出异常，让上层Handler捕获并关闭连接或返回错误
            throw new RejectedExecutionException(errorMsg);
        });

        executor.initialize();

        log.info("ThreadPool - WebSocket连接处理器初始化完成: core={}, max={}, queue={} (CPU核心数: {})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity(), CPU_COUNT);

        return executor;
    }

    /**
     * WebSocket业务处理器
     * 专用于WebSocket消息处理中的耗时业务操作（如Redis查询、数据库操作）
     * 与连接处理器分离，避免连接建立被业务处理阻塞
     * <p>
     * 【分类】I/O密集型 - Redis查询 + 数据库操作
     * 【策略】core = CPU×2, max = CPU×4 (支持高并发消息处理)
     */
    @Bean("websocketBusinessExecutor")
    public ThreadPoolTaskExecutor websocketBusinessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 - I/O密集型：Redis查询+数据库操作
        int corePoolSize = Math.max(8, CPU_COUNT * 2);
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 - 支持高并发消息处理
        int maxPoolSize = Math.max(32, CPU_COUNT * 4);
        executor.setMaxPoolSize(maxPoolSize);

        // 队列容量 - 缓冲消息处理请求
        executor.setQueueCapacity(2000);

        // 线程空闲时间
        executor.setKeepAliveSeconds(120);

        // 线程命名
        executor.setThreadNamePrefix("ws-business-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);

        // 拒绝策略：丢弃任务并记录（消息处理失败可以容忍，客户端可重试）
        // 增强监控：记录详细的拒绝信息
        executor.setRejectedExecutionHandler((task, ext) -> {
            log.error("ThreadPool - WebSocket业务任务被拒绝，任务已丢弃（可容忍）: pool=ws-business, " +
                            "activeCount={}, queueSize={}, maxPoolSize={}, taskClass={}",
                    ext.getActiveCount(), ext.getQueue().size(), ext.getMaximumPoolSize(),
                    task.getClass().getSimpleName());
            // 可以考虑发送错误响应给客户端，或者客户端重试
        });

        executor.initialize();

        log.info("ThreadPool - WebSocket业务处理器初始化完成: core={}, max={}, queue={} (CPU核心数: {})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity(), CPU_COUNT);

        return executor;
    }

    /**
     * Redis消息监听器专用线程池
     * 专用于Redis键过期事件监听处理，替代SimpleAsyncTaskExecutor避免线程泄漏
     * <p>
     * 【任务分析】实际执行：
     * • RedisKeysExpirationListener.onMessage() - Redis键过期事件处理
     * • handleDeviceStatusExpiration() - 设备状态过期处理 + RPC调用
     * • handleDeviceCommandExpiration() - 设备指令过期处理 + 数据库操作
     * <p>
     * 【分类】I/O密集型 - Redis事件处理 + 数据库操作 + RPC调用
     * 【策略】core = CPU×2, max = CPU×3 (支持Redis事件处理并发)
     */
    @Bean("redisMessageListenerExecutor")
    public ThreadPoolTaskExecutor redisMessageListenerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 - I/O密集型：Redis事件处理 + 数据库操作
        int corePoolSize = Math.max(4, CPU_COUNT * 2);
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数 - 支持Redis事件处理高峰
        int maxPoolSize = Math.max(16, CPU_COUNT * 3);
        executor.setMaxPoolSize(maxPoolSize);

        // 队列容量 - 缓冲Redis过期事件
        executor.setQueueCapacity(1000);

        // 线程空闲时间 - 较长保活，适应过期事件波动
        executor.setKeepAliveSeconds(300);

        // 线程命名
        executor.setThreadNamePrefix("redis-listener-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // 拒绝策略：Redis过期键监听不重要，直接丢弃（非关键任务）
        // 增强监控：记录拒绝事件
        executor.setRejectedExecutionHandler((task, ext) -> {
            log.warn("ThreadPool - Redis监听任务被拒绝，已丢弃（非关键任务）: pool=redis-listener, " +
                            "activeCount={}, queueSize={}, maxPoolSize={}, taskClass={}",
                    ext.getActiveCount(), ext.getQueue().size(), ext.getMaximumPoolSize(),
                    task.getClass().getSimpleName());
            // Redis过期键监听失败可接受，不影响核心业务
        });

        executor.initialize();

        log.info("ThreadPool - Redis消息监听器线程池初始化完成: core={}, max={}, queue={} (CPU核心数: {})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity(), CPU_COUNT);

        return executor;
    }

}
