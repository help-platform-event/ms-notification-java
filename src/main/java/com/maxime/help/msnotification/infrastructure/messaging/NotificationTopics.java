package com.maxime.help.msnotification.infrastructure.messaging;

/** Kafka topics this service reads (published by ms-auth) and its own internal email topic. */
final class NotificationTopics {

    static final String USER_REGISTERED = "auth.user.registered";
    static final String USER_SETTINGS_CHANGED = "auth.user.settings-changed";
    static final String PASSWORD_CHANGED = "auth.password.changed";

    /** Internal: emails waiting to be delivered, with retry topics and a dead-letter topic. */
    static final String EMAIL_REQUESTED = "notification.email.requested";

    private NotificationTopics() {}
}
