package com.maxime.help.msnotification.domain.port.out;

import com.maxime.help.msnotification.domain.model.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    /** One page of the user's notifications, newest first. */
    List<Notification> findLatestByUserId(UUID userId, int page, int size);

    long countUnreadByUserId(UUID userId);

    void markAllReadByUserId(UUID userId, Instant now);
}
