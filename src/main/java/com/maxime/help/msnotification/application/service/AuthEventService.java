package com.maxime.help.msnotification.application.service;

import com.maxime.help.msnotification.domain.model.NotificationPreferences;
import com.maxime.help.msnotification.domain.model.Recipient;
import com.maxime.help.msnotification.domain.port.out.EmailSender;
import com.maxime.help.msnotification.domain.port.out.ProcessedEventRepository;
import com.maxime.help.msnotification.domain.port.out.RecipientRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reacts to ms-auth's events: keeps the local projection of recipients up to date and sends the
 * transactional emails. Each method is idempotent on the event id — a redelivered event changes
 * nothing and sends nothing twice. If sending fails, the exception rolls the whole method back and
 * the event is consumed again later (see {@code KafkaConfig}).
 */
@Service
public class AuthEventService {

    private static final Logger log = LoggerFactory.getLogger(AuthEventService.class);

    private final RecipientRepository recipientRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EmailSender emailSender;

    AuthEventService(
            RecipientRepository recipientRepository,
            ProcessedEventRepository processedEventRepository,
            EmailSender emailSender) {
        this.recipientRepository = recipientRepository;
        this.processedEventRepository = processedEventRepository;
        this.emailSender = emailSender;
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
        emailSender.send(EmailContents.welcome(email));
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
                        email -> emailSender.send(EmailContents.passwordChanged(email)),
                        () -> log.warn("No known email for user {}: password-changed email skipped", userId));
        processedEventRepository.markProcessed(eventId);
    }
}
