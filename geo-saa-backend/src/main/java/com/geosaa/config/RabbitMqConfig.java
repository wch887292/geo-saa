package com.geosaa.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = false)
public class RabbitMqConfig {

    // ========== 审计日志 ==========
    public static final String QUEUE_AUDIT_LOG = "queue.audit.log";
    public static final String EXCHANGE_DIRECT = "exchange.direct";
    public static final String ROUTING_KEY_AUDIT_LOG = "routing.key.audit.log";

    // ========== 内容创作 ==========
    public static final String QUEUE_CONTENT_GENERATE = "queue.content.generate";
    public static final String EXCHANGE_CONTENT = "exchange.content";
    public static final String ROUTING_KEY_CONTENT_GENERATE = "routing.key.content.generate";

    // ========== 分发任务 ==========
    public static final String QUEUE_DISTRIBUTE_TASK = "queue.distribute.task";
    public static final String EXCHANGE_DISTRIBUTE = "exchange.distribute";
    public static final String ROUTING_KEY_DISTRIBUTE_TASK = "routing.key.distribute.task";

    // ========== 审计日志队列 ==========
    @Bean
    public Queue auditLogQueue() {
        return QueueBuilder.durable(QUEUE_AUDIT_LOG).build();
    }

    @Bean
    public DirectExchange directExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_DIRECT).durable(true).build();
    }

    @Bean
    public Binding auditLogBinding(Queue auditLogQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(auditLogQueue).to(directExchange).with(ROUTING_KEY_AUDIT_LOG);
    }

    // ========== 内容创作队列 ==========
    @Bean
    public Queue contentGenerateQueue() {
        return QueueBuilder.durable(QUEUE_CONTENT_GENERATE).build();
    }

    @Bean
    public DirectExchange contentExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_CONTENT).durable(true).build();
    }

    @Bean
    public Binding contentGenerateBinding(Queue contentGenerateQueue, DirectExchange contentExchange) {
        return BindingBuilder.bind(contentGenerateQueue).to(contentExchange).with(ROUTING_KEY_CONTENT_GENERATE);
    }

    // ========== 分发任务队列 ==========
    @Bean
    public Queue distributeTaskQueue() {
        return QueueBuilder.durable(QUEUE_DISTRIBUTE_TASK).build();
    }

    @Bean
    public DirectExchange distributeExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_DISTRIBUTE).durable(true).build();
    }

    @Bean
    public Binding distributeTaskBinding(Queue distributeTaskQueue, DirectExchange distributeExchange) {
        return BindingBuilder.bind(distributeTaskQueue).to(distributeExchange).with(ROUTING_KEY_DISTRIBUTE_TASK);
    }
}
