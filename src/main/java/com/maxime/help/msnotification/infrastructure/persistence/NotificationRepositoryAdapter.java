package com.maxime.help.msnotification.infrastructure.persistence;

import com.maxime.help.msnotification.domain.model.Notification;
import com.maxime.help.msnotification.domain.port.out.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Adapts Spring Data JPA to the {@link NotificationRepository} port. */
@Component
class NotificationRepositoryAdapter implements NotificationRepository {

    private final SpringDataNotificationJpaRepository jpa;

    NotificationRepositoryAdapter(SpringDataNotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Notification save(Notification notification) {
        return toDomain(jpa.save(toEntity(notification)));
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpa.findById(id).map(NotificationRepositoryAdapter::toDomain);
    }

    @Override
    public List<Notification> findLatestByUserId(UUID userId, int page, int size) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)).stream()
                .map(NotificationRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public long countUnreadByUserId(UUID userId) {
        return jpa.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    public void markAllReadByUserId(UUID userId, Instant now) {
        jpa.markAllRead(userId, now);
    }

    private static Notification toDomain(NotificationJpaEntity entity) {
        return Notification.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getTitle(),
                entity.getMessage(),
                entity.getCreatedAt(),
                entity.getReadAt());
    }

    private static NotificationJpaEntity toEntity(Notification notification) {
        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.setId(notification.getId());
        entity.setUserId(notification.getUserId());
        entity.setTitle(notification.getTitle());
        entity.setMessage(notification.getMessage());
        entity.setCreatedAt(notification.getCreatedAt());
        entity.setReadAt(notification.getReadAt().orElse(null));
        return entity;
    }
}
