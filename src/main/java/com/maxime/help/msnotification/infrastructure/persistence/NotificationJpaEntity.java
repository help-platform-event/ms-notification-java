package com.maxime.help.msnotification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA mapping for {@code notifications}. The id is assigned by the domain, not generated here. */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class NotificationJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "binary(16)", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "binary(16)", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;
}
