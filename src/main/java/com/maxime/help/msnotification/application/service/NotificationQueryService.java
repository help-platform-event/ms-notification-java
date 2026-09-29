package com.maxime.help.msnotification.application.service;

import com.maxime.help.msnotification.domain.model.Notification;
import com.maxime.help.msnotification.domain.port.out.NotificationRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the bell needs: a user's notifications, their unread count, and marking them read. */
@Service
public class NotificationQueryService {

    public static final int PAGE_SIZE = 20;

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    NotificationQueryService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Notification> latest(UUID userId, int page) {
        return notificationRepository.findLatestByUserId(userId, Math.max(page, 0), PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * Marks one of the user's notifications read. Returns false if it doesn't exist <em>or belongs
     * to someone else</em>: both look the same to the caller, so nobody learns another user's ids.
     */
    @Transactional
    public boolean markRead(UUID userId, UUID notificationId) {
        return notificationRepository
                .findById(notificationId)
                .filter(notification -> notification.belongsTo(userId))
                .map(notification -> {
                    notification.markRead(clock.instant());
                    notificationRepository.save(notification);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllReadByUserId(userId, clock.instant());
    }
}
