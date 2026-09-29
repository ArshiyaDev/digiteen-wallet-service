package com.digiteen.walletservice.outbox;

import com.digiteen.walletservice.config.RabbitConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "api", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxEventRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:100}")
    @Transactional
    public void publishBatch() {
        List<OutboxEventEntity> events = repository.lockUnpublished(50);
        for (OutboxEventEntity event : events) {
            try {
                Message message = MessageBuilder.withBody(event.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .setContentType("application/json")
                        .setMessageId(event.getId().toString())
                        .setHeader("eventType", event.getEventType())
                        .setHeader("traceId", event.getTraceId())
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .build();
                CorrelationData correlation = new CorrelationData(event.getId().toString());
                rabbitTemplate.send(RabbitConfiguration.EXCHANGE, RabbitConfiguration.ROUTING_KEY, message, correlation);
                CorrelationData.Confirm confirm = correlation.getFuture().get(1500, TimeUnit.MILLISECONDS);
                if (!confirm.isAck()) {
                    throw new IllegalStateException("RabbitMQ rejected event: " + confirm.getReason());
                }
                event.markPublished(Instant.now());
                log.atInfo().addKeyValue("event", "outbox_event_published")
                        .addKeyValue("eventId", event.getId()).addKeyValue("traceId", event.getTraceId())
                        .log("Wallet event published");
            } catch (Exception exception) {
                event.markFailed(exception.getMessage());
                log.atWarn().addKeyValue("event", "outbox_publish_failed")
                        .addKeyValue("eventId", event.getId()).addKeyValue("traceId", event.getTraceId())
                        .setCause(exception).log("Wallet event publish will be retried");
            }
        }
    }
}
