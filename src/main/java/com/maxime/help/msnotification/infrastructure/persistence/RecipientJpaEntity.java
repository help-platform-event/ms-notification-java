package com.maxime.help.msnotification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA mapping for {@code recipients}. The primary key is the user id from ms-auth, not generated. */
@Entity
@Table(name = "recipients")
@Getter
@Setter
@NoArgsConstructor
public class RecipientJpaEntity {

    @Id
    @Column(name = "user_id", columnDefinition = "binary(16)", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "email")
    private String email;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "notify_event_activity", nullable = false)
    private boolean eventActivity;

    @Column(name = "notify_event_messages", nullable = false)
    private boolean eventMessages;

    @Column(name = "notify_documents", nullable = false)
    private boolean documents;

    @Column(name = "notify_deadlines", nullable = false)
    private boolean deadlines;

    @Column(name = "notify_nearby_events", nullable = false)
    private boolean nearbyEvents;

    @Column(name = "notify_judgments", nullable = false)
    private boolean judgments;
}
