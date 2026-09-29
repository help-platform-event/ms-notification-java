package com.maxime.help.msnotification.web.controller;

import com.maxime.help.msnotification.domain.model.Notification;
import com.maxime.help.msnotification.domain.port.out.NotificationPusher;
import com.maxime.help.msnotification.web.dto.NotificationResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The open Server-Sent Events streams, one per browser tab, grouped by user. A new notification is
 * written to its recipient's streams as an {@code event: notification}, once the transaction that
 * saved it commits (same pattern as ms-auth-java's Kafka publisher).
 *
 * <p>In memory, so it only works with a single instance of the service: with several, a user's
 * stream and their notification could land on different instances.
 */
@Component
class SseNotificationStreams implements NotificationPusher {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationStreams.class);

    private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    /** Opens a stream for the user; it closes itself after {@code timeout}. */
    SseEmitter subscribe(UUID userId, Duration timeout) {
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        register(userId, emitter);
        // Sent right away, so the client sees the stream open without waiting for a notification.
        send(userId, emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    /** Package-private so tests can register an emitter that records what it's sent. */
    void register(UUID userId, SseEmitter emitter) {
        streams.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        // Expected end of the stream (the token expired): completing it here ends the response
        // cleanly, instead of Spring logging an AsyncRequestTimeoutException for every stream.
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> remove(userId, emitter));
        log.debug("Stream opened for user {}", userId);
    }

    @Override
    public void push(Notification notification) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendToRecipient(notification);
                }
            });
        } else {
            sendToRecipient(notification);
        }
    }

    /**
     * An SSE comment every 25 s. It keeps idle connections open through the Gateway and proxies,
     * and a write to a closed connection fails, which removes the streams of users who left.
     */
    @Scheduled(fixedRate = 25, timeUnit = TimeUnit.SECONDS)
    void heartbeat() {
        streams.forEach((userId, emitters) ->
                emitters.forEach(emitter -> send(userId, emitter, SseEmitter.event().comment("keep-alive"))));
    }

    private void sendToRecipient(Notification notification) {
        UUID userId = notification.getUserId();
        NotificationResponse payload = NotificationController.toResponse(notification);
        streams.getOrDefault(userId, Set.of())
                .forEach(emitter -> send(userId, emitter, SseEmitter.event().name("notification").data(payload)));
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // The client is gone (closed tab, lost network) or the stream already completed.
            remove(userId, emitter);
            emitter.completeWithError(e);
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
        log.debug("Stream closed for user {}", userId);
    }
}
