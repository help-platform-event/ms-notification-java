package com.maxime.help.msnotification.infrastructure.messaging;

import com.maxime.help.msnotification.application.service.AuthEventService;
import com.maxime.help.msnotification.domain.model.NotificationPreferences;
import com.maxime.help.msnotification.infrastructure.messaging.dto.PasswordChangedMessage;
import com.maxime.help.msnotification.infrastructure.messaging.dto.UserRegisteredMessage;
import com.maxime.help.msnotification.infrastructure.messaging.dto.UserSettingsChangedMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes ms-auth's events (consumer group {@code spring.kafka.consumer.group-id}) and hands them to
 * the application layer. Offsets are committed after each successful call: a failure leaves the
 * record to be consumed again.
 */
@Component
class AuthEventListener {

    private final AuthEventService authEventService;

    AuthEventListener(AuthEventService authEventService) {
        this.authEventService = authEventService;
    }

    @KafkaListener(topics = NotificationTopics.USER_REGISTERED)
    void onUserRegistered(UserRegisteredMessage message) {
        authEventService.onUserRegistered(message.eventId(), message.userId(), message.email());
    }

    @KafkaListener(topics = NotificationTopics.USER_SETTINGS_CHANGED)
    void onUserSettingsChanged(UserSettingsChangedMessage message) {
        UserSettingsChangedMessage.Notifications n = message.notifications();
        authEventService.onUserSettingsChanged(
                message.eventId(),
                message.userId(),
                new NotificationPreferences(
                        n.enabled(),
                        n.eventActivity(),
                        n.eventMessages(),
                        n.documents(),
                        n.deadlines(),
                        n.nearbyEvents(),
                        n.judgments()));
    }

    @KafkaListener(topics = NotificationTopics.PASSWORD_CHANGED)
    void onPasswordChanged(PasswordChangedMessage message) {
        authEventService.onPasswordChanged(message.eventId(), message.userId());
    }
}
