package com.maxime.help.msnotification.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire format of the Gateway's {@code event.participation.requested}: someone asked to join a slot,
 * the recipient is the event's organizer. Mirrors {@code ParticipationRequestedEvent} in event-app's
 * {@code packages/contracts}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParticipationRequestedMessage(
        UUID eventId,
        Instant occurredAt,
        long participationId,
        UUID recipientUserId,
        UUID actorUserId,
        EventRef event,
        SlotRef slot) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventRef(long id, String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SlotRef(long id, Instant startAt) {}
}
