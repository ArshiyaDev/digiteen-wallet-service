package com.digiteen.walletservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    public static final String EXCHANGE = "wallet.events";
    public static final String QUEUE = "wallet.audit";
    public static final String ROUTING_KEY = "wallet.transaction.succeeded";

    @Bean
    DirectExchange walletExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue walletAuditQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    Binding walletAuditBinding(Queue walletAuditQueue, DirectExchange walletExchange) {
        return BindingBuilder.bind(walletAuditQueue).to(walletExchange).with(ROUTING_KEY);
    }
}
