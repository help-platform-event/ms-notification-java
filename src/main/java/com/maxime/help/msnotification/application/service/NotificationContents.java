package com.maxime.help.msnotification.application.service;

import com.maxime.help.msnotification.domain.model.Notification;
import java.time.Instant;

/** Texts of the in-app notifications (the bell): shorter than the emails, same information. */
final class NotificationContents {

    private NotificationContents() {}

    static Notification participationRequested(ParticipationActivity activity, Instant now) {
        return Notification.create(
                activity.recipientUserId(),
                "Nouvelle demande de participation",
                "Un bénévole demande à participer au créneau du %s de « %s »."
                        .formatted(EmailContents.slotDate(activity), activity.eventTitle()),
                now);
    }

    static Notification participationDecided(ParticipationActivity activity, boolean accepted, Instant now) {
        String slot = EmailContents.slotDate(activity);
        return Notification.create(
                activity.recipientUserId(),
                accepted ? "Participation acceptée" : "Participation refusée",
                (accepted
                                ? "Votre participation au créneau du %s de « %s » a été acceptée."
                                : "Votre participation au créneau du %s de « %s » n'a pas été retenue.")
                        .formatted(slot, activity.eventTitle()),
                now);
    }
}
