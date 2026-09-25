package com.maxime.help.msnotification.infrastructure.messaging;

import com.maxime.help.msnotification.domain.model.EmailRequest;
import com.maxime.help.msnotification.domain.port.out.EmailRequestPublisher;
import com.maxime.help.msnotification.infrastructure.messaging.dto.EmailRequestMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Queues email requests on {@code notification.email.requested}, keyed by recipient. Like ms-auth's
 * publisher, the send waits for the enclosing transaction to commit, so a rolled-back handling
 * never queues an email.
 *
 * <p>The value is serialized to a JSON string here (the producer uses a plain StringSerializer):
 * when a delivery fails, Spring re-publishes the consumed record — a string — to the retry and
 * dead-letter topics, and a JSON serializer would wrap it in quotes instead of copying it as is.
 */
@Component
class KafkaEmailRequestPublisherAdapter implements EmailRequestPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    KafkaEmailRequestPublisherAdapter(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void publish(EmailRequest request) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(request);
                }
            });
        } else {
            send(request);
        }
    }

    private void send(EmailRequest request) {
        String json = jsonMapper.writeValueAsString(new EmailRequestMessage(
                request.requestId(), request.recipientUserId(), request.to(), request.subject(), request.body()));
        kafkaTemplate.send(NotificationTopics.EMAIL_REQUESTED, request.recipientUserId().toString(), json);
    }
}
