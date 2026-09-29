package com.maxime.help.msnotification.web.controller;

import com.maxime.help.msnotification.application.service.NotificationQueryService;
import com.maxime.help.msnotification.domain.model.Notification;
import com.maxime.help.msnotification.web.dto.NotificationResponse;
import com.maxime.help.msnotification.web.dto.UnreadCountResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The current user's in-app notifications. The user is the {@code sub} claim of ms-auth's access
 * token: a user can only ever reach their own notifications.
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    /** Stream lifetime when the token carries no expiry (ms-auth's access tokens always do). */
    private static final Duration DEFAULT_STREAM_TIMEOUT = Duration.ofMinutes(15);

    /**
     * The JWT decoder accepts a token up to 60 s past its expiry (clock skew), which would give a
     * negative timeout; and a timeout of 0 would mean "never" to the servlet container.
     */
    private static final Duration MIN_STREAM_TIMEOUT = Duration.ofSeconds(1);

    private final NotificationQueryService notificationQueryService;
    private final SseNotificationStreams streams;
    private final Clock clock;

    NotificationController(
            NotificationQueryService notificationQueryService, SseNotificationStreams streams, Clock clock) {
        this.notificationQueryService = notificationQueryService;
        this.streams = streams;
        this.clock = clock;
    }

    /**
     * The user's new notifications, pushed as Server-Sent Events. The stream closes when the access
     * token expires: the client then reconnects with a fresh one, so a stream never outlives the
     * token that opened it.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        Duration timeout =
                expiresAt == null ? DEFAULT_STREAM_TIMEOUT : Duration.between(clock.instant(), expiresAt);
        return streams.subscribe(userId(jwt), timeout.compareTo(MIN_STREAM_TIMEOUT) < 0 ? MIN_STREAM_TIMEOUT : timeout);
    }

    @GetMapping
    List<NotificationResponse> latest(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
        return notificationQueryService.latest(userId(jwt), page).stream()
                .map(NotificationController::toResponse)
                .toList();
    }

    /** The bell's badge, loaded when the page opens and each time the stream (re)connects. */
    @GetMapping("/unread-count")
    UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(notificationQueryService.unreadCount(userId(jwt)));
    }

    @PatchMapping("/{id}/read")
    ResponseEntity<?> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        if (!notificationQueryService.markRead(userId(jwt), id)) {
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Notification not found"))
                    .build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notificationQueryService.markAllRead(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.isRead());
    }
}
