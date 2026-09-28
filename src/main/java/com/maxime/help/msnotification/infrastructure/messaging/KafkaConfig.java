package com.maxime.help.msnotification.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
class KafkaConfig {

    /** Same as the source topics, so a dead letter keeps its original partition number. */
    private static final int PARTITIONS = 3;

    /**
     * Records are consumed as plain strings, then converted to each {@code @KafkaListener} method's
     * parameter type (e.g. {@code UserRegisteredMessage}). No type header is needed: the producers
     * don't send one, and the listener's signature says what the topic contains. Spring Boot
     * applies this converter to the default listener container factory.
     */
    @Bean
    RecordMessageConverter recordMessageConverter() {
        return new StringJacksonJsonMessageConverter();
    }

    /**
     * What happens when a listener throws (e.g. the SMTP server is down): the record is retried
     * after 2 s, 4 s and 8 s, then published to {@code <topic>-dlt} and the consumer moves on. The
     * dead-letter topic keeps the failed message for inspection or replay. A message that can't
     * be parsed is not retried: it goes to the dead-letter topic at once. Spring Boot applies this
     * handler to the default listener container factory.
     */
    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        ExponentialBackOff backOff = new ExponentialBackOff(2_000, 2.0);
        backOff.setMaxAttempts(3);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), backOff);
    }

    /** This service writes the dead-letter topics, so it declares them (one per consumed topic). */
    @Bean
    KafkaAdmin.NewTopics deadLetterTopics() {
        return new KafkaAdmin.NewTopics(NotificationTopics.ALL.stream()
                .map(topic -> TopicBuilder.name(NotificationTopics.deadLetterTopic(topic))
                        .partitions(PARTITIONS)
                        .build())
                .toArray(NewTopic[]::new));
    }
}
