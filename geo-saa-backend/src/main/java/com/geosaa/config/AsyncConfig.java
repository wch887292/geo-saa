package com.geosaa.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 *
 * <p>此前项目只加了 {@code @EnableAsync} 而没有定义 Executor，Spring 会回退到
 * {@code SimpleAsyncTaskExecutor}——每个任务新建一条线程且不复用、不设上限。
 * 诊断和批量内容生成都会走异步，高并发下足以把线程数打爆。
 * 这里换成有界队列 + CallerRuns 拒绝策略：队列满时由调用线程兜底执行，
 * 宁可变慢也不丢任务。
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    public static final String EXECUTOR_NAME = "geoTaskExecutor";

    /**
     * 唯一线程池实例。
     *
     * <p>修复前 {@link #getAsyncExecutor()} 每次都调用 {@link #geoTaskExecutor()} 新建实例，
     * 导致未显式指定执行器的 {@code @Async} 方法（如审计日志）每次解析都会新建一个线程池，
     * 线程池数量随方法数增长、空转浪费。这里统一复用同一个实例。
     */
    private final ThreadPoolTaskExecutor geoExecutor = createGeoTaskExecutor();

    @Bean(name = EXECUTOR_NAME)
    public ThreadPoolTaskExecutor geoTaskExecutor() {
        return geoExecutor;
    }

    private static ThreadPoolTaskExecutor createGeoTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cores = Runtime.getRuntime().availableProcessors();
        // AI 调用属于 IO 密集型，核心线程数可以高于 CPU 核数
        executor.setCorePoolSize(Math.max(4, cores * 2));
        executor.setMaxPoolSize(Math.max(8, cores * 4));
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("geo-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等待在途任务完成，避免任务状态永远卡在“处理中”
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return geoExecutor;
    }

    /**
     * 返回 void 的 {@code @Async} 方法抛出的异常不会传播给调用方，
     * 没有这个处理器就会被彻底吞掉。
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, java.lang.reflect.Method method, Object... params) {
                log.error("异步任务执行异常: method={}", method.getName(), ex);
            }
        };
    }
}
