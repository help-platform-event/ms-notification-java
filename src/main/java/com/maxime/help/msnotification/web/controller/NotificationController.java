package com.maxime.help.msnotification.web.controller;

import com.maxime.help.msnotification.application.service.NotificationQueryService;
import com.maxime.help.msnotification.domain.model.Notification;
import com.maxime.help.msnotification.web.dto.NotificationResponse;
import com.maxime.help.msnotification.web.dto.UnreadCountResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

/**
 * The current user's in-app notifications. The user is the {@code sub} claim of ms-auth's access
 * token: a user can only ever reach their own notifications.
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationQueryService notificationQueryService;

    NotificationController(NotificationQueryService notificationQueryService) {
        this.notificationQueryService = notificationQueryService;
    }

    @GetMapping
    List<NotificationResponse> latest(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
        return notificationQueryService.latest(userId(jwt), page).stream()
                .map(NotificationController::toResponse)
                .toList();
    }

    /** The request the Front polls to refresh the bell's badge. */
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

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.isRead());
    }
}
