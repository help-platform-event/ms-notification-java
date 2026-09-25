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
}
