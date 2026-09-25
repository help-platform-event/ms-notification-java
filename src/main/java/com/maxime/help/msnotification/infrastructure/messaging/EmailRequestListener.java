package com.maxime.help.msnotification.infrastructure.messaging;

import com.maxime.help.msnotification.application.service.EmailDeliveryService;
import com.maxime.help.msnotification.domain.model.EmailRequest;
import com.maxime.help.msnotification.infrastructure.messaging.dto.EmailRequestMessage;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

/**
 * Delivers queued emails, with non-blocking retries: a failed request moves to a retry topic
 * ({@code notification.email.requested-retry-*}) and is tried again after a growing delay, while
 * the main topic keeps flowing. After the last attempt it lands on the dead-letter topic
 * ({@code notification.email.requested-dlt}), which keeps it for inspection or replay. A message
 * that can't even be parsed is not retried: it goes straight to the dead-letter topic.
 */
@Component
class EmailRequestListener {

    private static final Logger log = LoggerFactory.getLogger(EmailRequestListener.class);

    private final EmailDeliveryService emailDeliveryService;

    EmailRequestListener(EmailDeliveryService emailDeliveryService) {
        this.emailDeliveryService = emailDeliveryService;
    }

    @RetryableTopic(
            attempts = "${app.email.retry.attempts:4}",
            backOff = @BackOff(
                    delayString = "${app.email.retry.initial-delay-ms:2000}",
                    multiplierString = "${app.email.retry.multiplier:2.0}"))
    @KafkaListener(topics = NotificationTopics.EMAIL_REQUESTED)
    void onEmailRequested(EmailRequestMessage message) {
        emailDeliveryService.deliver(new EmailRequest(
                message.requestId(), message.recipientUserId(), message.to(), message.subject(), message.body()));
    }

    /** Raw record on purpose: an unparseable message must still be handled here. */
    @DltHandler
    void onDeadLetter(ConsumerRecord<String, String> record) {
        log.error(
                "Email request dead-lettered (topic={}, partition={}, offset={}, key={}): {}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE));
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? "(no reason recorded)" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
