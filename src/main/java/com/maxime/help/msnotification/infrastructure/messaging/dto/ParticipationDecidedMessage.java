package com.maxime.help.msnotification.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.maxime.help.msnotification.infrastructure.messaging.dto.ParticipationRequestedMessage.EventRef;
import com.maxime.help.msnotification.infrastructure.messaging.dto.ParticipationRequestedMessage.SlotRef;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire format of the Gateway's {@code event.participation.decided}: the organizer accepted or
 * rejected a request, the recipient is the participant. An unknown {@code status} fails the
 * conversion, and Spring Kafka logs and skips such a record instead of retrying it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParticipationDecidedMessage(
        UUID eventId,
        Instant occurredAt,
        long participationId,
        UUID recipientUserId,
        UUID actorUserId,
        EventRef event,
        SlotRef slot,
        Decision status) {

    public enum Decision {
        ACCEPTED,
        REJECTED
    }
}
