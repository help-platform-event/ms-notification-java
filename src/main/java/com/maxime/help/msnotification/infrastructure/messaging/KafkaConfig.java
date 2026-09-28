package com.maxime.help.msnotification.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;

@Configuration
class KafkaConfig {

    /**
     * Records are consumed as plain strings, then converted to each {@code @KafkaListener} method's
     * parameter type (e.g. {@code UserRegisteredMessage}). No type header is needed: ms-auth
     * doesn't send one, and the listener's signature says what the topic contains. Spring Boot
     * applies this converter to the default listener container factory.
     */
    @Bean
    RecordMessageConverter recordMessageConverter() {
        return new StringJacksonJsonMessageConverter();
    }

    /** Same partitioning as ms-auth's topics: keyed by user id, 3 partitions. */
    @Bean
    NewTopic emailRequestedTopic() {
        return TopicBuilder.name(NotificationTopics.EMAIL_REQUESTED).partitions(3).build();
    }

    /**
     * The Gateway creates its topics too, but only once it connects to Kafka. Declaring them here as
     * well matters: otherwise, if this service starts first, subscribing to a missing topic makes
     * the broker auto-create it with a single partition, and the Gateway's "create if absent" then
     * keeps that one partition. Declaring the same topic on both sides is harmless.
     */
    @Bean
    NewTopic participationRequestedTopic() {
        return TopicBuilder.name(NotificationTopics.PARTICIPATION_REQUESTED).partitions(3).build();
    }

    @Bean
    NewTopic participationDecidedTopic() {
        return TopicBuilder.name(NotificationTopics.PARTICIPATION_DECIDED).partitions(3).build();
    }
}
