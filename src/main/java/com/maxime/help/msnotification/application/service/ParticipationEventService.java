package com.maxime.help.msnotification.application.service;

import com.maxime.help.msnotification.domain.model.Email;
import com.maxime.help.msnotification.domain.model.NotificationCategory;
import com.maxime.help.msnotification.domain.model.Recipient;
import com.maxime.help.msnotification.domain.port.out.EmailSender;
import com.maxime.help.msnotification.domain.port.out.ProcessedEventRepository;
import com.maxime.help.msnotification.domain.port.out.RecipientRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reacts to the Gateway's participation events. Unlike the transactional auth emails, these are
 * {@link NotificationCategory#EVENT_ACTIVITY} notifications: sent only if the recipient's
 * preferences allow them. Every event is marked processed, whether or not something was sent, so a
 * redelivery is ignored either way.
 */
@Service
public class ParticipationEventService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationEventService.class);

    private final RecipientRepository recipientRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EmailSender emailSender;

    ParticipationEventService(
            RecipientRepository recipientRepository,
            ProcessedEventRepository processedEventRepository,
            EmailSender emailSender) {
        this.recipientRepository = recipientRepository;
        this.processedEventRepository = processedEventRepository;
        this.emailSender = emailSender;
    }

    @Transactional
    public void onParticipationRequested(UUID eventId, ParticipationActivity activity) {
        notifyIfAllowed(eventId, activity, EmailContents::participationRequested);
    }

    @Transactional
    public void onParticipationDecided(UUID eventId, ParticipationActivity activity, boolean accepted) {
        notifyIfAllowed(eventId, activity, (to, a) -> EmailContents.participationDecided(to, a, accepted));
    }

    private void notifyIfAllowed(
            UUID eventId,
            ParticipationActivity activity,
            BiFunction<String, ParticipationActivity, Email> email) {
        if (processedEventRepository.isProcessed(eventId)) {
            return;
        }
        emailAddressFor(activity)
                .ifPresent(to -> emailSender.send(email.apply(to, activity)));
        processedEventRepository.markProcessed(eventId);
    }

    /** The address to write to, or empty if this notification must not (or cannot) be sent. */
    private Optional<String> emailAddressFor(ParticipationActivity activity) {
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
        Optional<String> email = recipient.get().getEmail();
        if (email.isEmpty()) {
            log.warn("No known email for user {}: participation {} not notified", userId, activity.participationId());
        }
        return email;
    }
}
