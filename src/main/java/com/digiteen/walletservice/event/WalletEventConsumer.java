package com.digiteen.walletservice.event;

import com.digiteen.walletservice.config.RabbitConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "consumer")
public class WalletEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(WalletEventConsumer.class);
    private final ConsumedEventRepository repository;

    public WalletEventConsumer(ConsumedEventRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = RabbitConfiguration.QUEUE)
    @Transactional
    public void consume(Message message) {
        UUID eventId = UUID.fromString(message.getMessageProperties().getMessageId());
        String eventType = String.valueOf(message.getMessageProperties().getHeaders().get("eventType"));
        String traceId = String.valueOf(message.getMessageProperties().getHeaders().get("traceId"));
        int inserted = repository.insertIfAbsent(eventId, eventType, traceId, Instant.now());
        log.atInfo().addKeyValue("event", inserted == 1 ? "wallet_event_consumed" : "duplicate_event_ignored")
                .addKeyValue("eventId", eventId).addKeyValue("traceId", traceId)
                .log(inserted == 1 ? "Wallet event consumed" : "Duplicate wallet event ignored");
    }
}
