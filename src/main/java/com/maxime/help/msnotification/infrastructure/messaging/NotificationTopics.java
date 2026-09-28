package com.maxime.help.msnotification.infrastructure.messaging;

import java.util.List;

/** Kafka topics this service reads. It doesn't create them: whoever publishes a topic declares it. */
final class NotificationTopics {

    /** Published by ms-auth-java. */
    static final String USER_REGISTERED = "auth.user.registered";
    static final String USER_SETTINGS_CHANGED = "auth.user.settings-changed";
    static final String PASSWORD_CHANGED = "auth.password.changed";

    /** Published by the NestJS Gateway (kafkajs), keyed by the recipient's user id. */
    static final String PARTICIPATION_REQUESTED = "event.participation.requested";
    static final String PARTICIPATION_DECIDED = "event.participation.decided";
    static final String PARTICIPATION_CANCELLED = "event.participation.cancelled";

    static final List<String> ALL = List.of(
            USER_REGISTERED,
            USER_SETTINGS_CHANGED,
            PASSWORD_CHANGED,
            PARTICIPATION_REQUESTED,
            PARTICIPATION_DECIDED,
            PARTICIPATION_CANCELLED);

    /** Where Spring Kafka's {@code DeadLetterPublishingRecoverer} sends a failed record by default. */
    static String deadLetterTopic(String topic) {
        return topic + "-dlt";
    }

    private NotificationTopics() {}
}
