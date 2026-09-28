package com.maxime.help.msnotification.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository over {@link NotificationJpaEntity}. Package-private: only the adapter uses it. */
interface SpringDataNotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    List<NotificationJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("update NotificationJpaEntity n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    void markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
