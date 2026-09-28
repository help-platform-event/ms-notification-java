package com.maxime.help.msnotification.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.maxime.help.msnotification.infrastructure.messaging.dto.ParticipationRequestedMessage.EventRef;
import com.maxime.help.msnotification.infrastructure.messaging.dto.ParticipationRequestedMessage.SlotRef;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire format of the Gateway's {@code event.participation.cancelled}: a participation was cancelled
 * by the volunteer (the recipient is the organizer) or by the organizer (the recipient is the
 * volunteer). An unknown {@code cancelledBy} fails the conversion, and the record goes to the DLT.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParticipationCancelledMessage(
        UUID eventId,
        Instant occurredAt,
        long participationId,
        UUID recipientUserId,
        UUID actorUserId,
        EventRef event,
        SlotRef slot,
        CancelledBy cancelledBy) {

    public enum CancelledBy {
        PARTICIPANT,
        ORGANIZER
    }
}
