package com.maxime.help.msnotification.application.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What happened to a participation, as far as a notification needs to know: who to tell, who did it,
 * and which event and slot it is about.
 */
public record ParticipationActivity(
        long participationId, UUID recipientUserId, UUID actorUserId, String eventTitle, Instant slotStartAt) {

    public ParticipationActivity {
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        Objects.requireNonNull(actorUserId, "actorUserId");
        Objects.requireNonNull(eventTitle, "eventTitle");
        Objects.requireNonNull(slotStartAt, "slotStartAt");
    }

    /** E.g. an organizer joining their own event's slot: nobody else to tell. */
    boolean isSelfTriggered() {
        return recipientUserId.equals(actorUserId);
    }
}
