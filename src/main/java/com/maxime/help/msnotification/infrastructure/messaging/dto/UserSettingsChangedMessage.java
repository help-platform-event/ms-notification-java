package com.maxime.help.msnotification.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire format of ms-auth's {@code auth.user.settings-changed} (a full snapshot). Only the
 * notification part is read; availability is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserSettingsChangedMessage(
        UUID eventId, Instant occurredAt, UUID userId, Notifications notifications) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Notifications(
            boolean enabled,
            boolean eventActivity,
            boolean eventMessages,
            boolean documents,
            boolean deadlines,
            boolean nearbyEvents,
            boolean judgments) {}
}
