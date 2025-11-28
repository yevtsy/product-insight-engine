package com.google.ai.insight.aggregator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for thread pool used by LLM agents.
 * Provides isolated executor for parallel agent execution with configurable limits.
 */
@Slf4j
@Configuration
public class AgentExecutorConfig {

    @Value("${agent-executor.core-pool-size:3}")
    private int corePoolSize;

    @Value("${agent-executor.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${agent-executor.queue-capacity:50}")
    private int queueCapacity;

    @Value("${agent-executor.thread-name-prefix:llm-agent-}")
    private String threadNamePrefix;

    @Bean(name = "agentExecutor")
    public Executor agentExecutor() {
        log.info("Initializing Agent Executor Thread Pool: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);

        // Use CallerRunsPolicy to prevent task rejection
        // When queue is full, the calling thread executes the task
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("Agent Executor Thread Pool initialized successfully");
        return executor;
    }
}