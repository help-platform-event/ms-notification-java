package com.maxime.help.msnotification.application.service;

import com.maxime.help.msnotification.domain.model.Email;
import com.maxime.help.msnotification.domain.model.Notification;
import com.maxime.help.msnotification.domain.model.NotificationCategory;
import com.maxime.help.msnotification.domain.model.Recipient;
import com.maxime.help.msnotification.domain.port.out.EmailSender;
import com.maxime.help.msnotification.domain.port.out.NotificationPusher;
import com.maxime.help.msnotification.domain.port.out.NotificationRepository;
import com.maxime.help.msnotification.domain.port.out.ProcessedEventRepository;
import com.maxime.help.msnotification.domain.port.out.RecipientRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reacts to the Gateway's participation events. Unlike the transactional auth emails, these are
 * {@link NotificationCategory#EVENT_ACTIVITY} notifications, sent only if the recipient's
 * preferences allow them: an in-app notification (the bell, pushed live to the recipient's open
 * tabs once the transaction commits), then an email if the address is known. Every event is
 * marked processed, whether or not something was sent, so a redelivery is ignored either way. If
 * the email fails, the whole method rolls back (in-app notification included, never pushed) and
 * the event is consumed again.
 */
@Service
public class ParticipationEventService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationEventService.class);

    private final RecipientRepository recipientRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPusher notificationPusher;
    private final EmailSender emailSender;
    private final Clock clock;

    ParticipationEventService(
            RecipientRepository recipientRepository,
            ProcessedEventRepository processedEventRepository,
            NotificationRepository notificationRepository,
            NotificationPusher notificationPusher,
            EmailSender emailSender,
            Clock clock) {
        this.recipientRepository = recipientRepository;
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
        this.notificationPusher = notificationPusher;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    @Transactional
    public void onParticipationRequested(UUID eventId, ParticipationActivity activity) {
        notifyIfAllowed(
                eventId,
                activity,
                NotificationContents.participationRequested(activity, clock.instant()),
                to -> EmailContents.participationRequested(to, activity));
    }

    @Transactional
    public void onParticipationDecided(UUID eventId, ParticipationActivity activity, boolean accepted) {
        notifyIfAllowed(
                eventId,
                activity,
                NotificationContents.participationDecided(activity, accepted, clock.instant()),
                to -> EmailContents.participationDecided(to, activity, accepted));
    }

    @Transactional
    public void onParticipationCancelled(UUID eventId, ParticipationActivity activity, boolean byOrganizer) {
        notifyIfAllowed(
                eventId,
                activity,
                NotificationContents.participationCancelled(activity, byOrganizer, clock.instant()),
                to -> EmailContents.participationCancelled(to, activity, byOrganizer));
    }

    private void notifyIfAllowed(
            UUID eventId, ParticipationActivity activity, Notification inApp, Function<String, Email> email) {
        if (processedEventRepository.isProcessed(eventId)) {
            return;
        }
        recipientToNotify(activity).ifPresent(recipient -> {
            notificationRepository.save(inApp);
            notificationPusher.push(inApp);
            recipient.getEmail().ifPresentOrElse(
                    to -> emailSender.send(email.apply(to)),
                    () -> log.warn("No known email for user {}: participation {} notified in-app only",
                            recipient.getUserId(), activity.participationId()));
        });
        processedEventRepository.markProcessed(eventId);
    }

    /** The recipient to notify, or empty if this notification must not be sent. */
    private Optional<Recipient> recipientToNotify(ParticipationActivity activity) {
        UUID userId = activity.recipientUserId();
        if (activity.isSelfTriggered()) {
            return Optional.empty();
        }
        Optional<Recipient> recipient = recipientRepository.findByUserId(userId);
        if (recipient.isEmpty()) {
            log.warn("Unknown recipient {}: participation {} not notified", userId, activity.participationId());
            return Optional.empty();
        }
        if (!recipient.get().accepts(NotificationCategory.EVENT_ACTIVITY)) {
            log.debug("Recipient {} muted event activity: participation {} not notified", userId, activity.participationId());
            return Optional.empty();
        }
        return recipient;
    }
}
