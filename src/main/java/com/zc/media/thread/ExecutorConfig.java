package com.zc.media.thread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @program: media
 * @description:异步线程池配置类
 * @author: Claire
 * @create: 2021-06-30 15:30
 **/

@Configuration
@EnableAsync
public class ExecutorConfig {
    private static final Logger logger = LoggerFactory.getLogger(ExecutorConfig.class);
    private static final String NAMEPREFIX = "async-service-";
    public static ThreadPoolTaskExecutor executor = new VisiableThreadPoolTaskExecutor();
    @Bean
    public Executor asyncServiceExecutor() {
        logger.info("start asyncServiceExecutor");
        //ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        //配置核心线程数
        executor.setCorePoolSize(50);
        //配置最大线程数
        executor.setMaxPoolSize(100);
        //配置队列大小
        executor.setQueueCapacity(1000);
        //线程空闲时间
        executor.setKeepAliveSeconds(1000);
        //配置线程池中的线程的名称前缀
        executor.setThreadNamePrefix(NAMEPREFIX);

        // rejection-policy：当pool已经达到max size的时候，如何处理新任务
        // CALLER_RUNS：不在新线程中执行任务，而是有调用者所在的线程来执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        //执行初始化
        executor.initialize();

        return executor;
    }

}
