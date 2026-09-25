package com.maxime.help.msnotification.application.service;

import com.maxime.help.msnotification.domain.model.NotificationPreferences;
import com.maxime.help.msnotification.domain.model.Recipient;
import com.maxime.help.msnotification.domain.port.out.EmailRequestPublisher;
import com.maxime.help.msnotification.domain.port.out.ProcessedEventRepository;
import com.maxime.help.msnotification.domain.port.out.RecipientRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reacts to ms-auth's events: keeps the local projection of recipients up to date and requests the
 * transactional emails. Each method is idempotent on the event id — a redelivered event changes
 * nothing and sends nothing twice.
 */
@Service
public class AuthEventService {

    private static final Logger log = LoggerFactory.getLogger(AuthEventService.class);

    private final RecipientRepository recipientRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EmailRequestPublisher emailRequestPublisher;

    AuthEventService(
            RecipientRepository recipientRepository,
            ProcessedEventRepository processedEventRepository,
            EmailRequestPublisher emailRequestPublisher) {
        this.recipientRepository = recipientRepository;
        this.processedEventRepository = processedEventRepository;
        this.emailRequestPublisher = emailRequestPublisher;
    }

    @Transactional
    public void onUserRegistered(UUID eventId, UUID userId, String email) {
        if (processedEventRepository.isProcessed(eventId)) {
            return;
        }
        Recipient recipient = recipientRepository
                .findByUserId(userId)
                .map(existing -> {
                    existing.registerEmail(email);
                    return existing;
                })
                .orElseGet(() -> Recipient.registered(userId, email));
        recipientRepository.save(recipient);
        emailRequestPublisher.publish(EmailContents.welcome(userId, email));
        processedEventRepository.markProcessed(eventId);
    }

    @Transactional
    public void onUserSettingsChanged(UUID eventId, UUID userId, NotificationPreferences preferences) {
        if (processedEventRepository.isProcessed(eventId)) {
            return;
        }
        Recipient recipient = recipientRepository
                .findByUserId(userId)
                .map(existing -> {
                    existing.changePreferences(preferences);
                    return existing;
                })
                .orElseGet(() -> Recipient.withPreferencesOnly(userId, preferences));
        recipientRepository.save(recipient);
        processedEventRepository.markProcessed(eventId);
    }

    @Transactional
    public void onPasswordChanged(UUID eventId, UUID userId) {
        if (processedEventRepository.isProcessed(eventId)) {
            return;
        }
        recipientRepository
                .findByUserId(userId)
                .flatMap(Recipient::getEmail)
                .ifPresentOrElse(
                        email -> emailRequestPublisher.publish(EmailContents.passwordChanged(userId, email)),
                        () -> log.warn("No known email for user {}: password-changed email skipped", userId));
        processedEventRepository.markProcessed(eventId);
    }
}
