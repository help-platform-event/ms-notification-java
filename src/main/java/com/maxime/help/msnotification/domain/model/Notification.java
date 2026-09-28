package com.maxime.help.msnotification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** An in-app notification, shown in the user's bell until they read it. */
public final class Notification {

    private final UUID id;
    private final UUID userId;
    private final String title;
    private final String message;
    private final Instant createdAt;
    private Instant readAt;

    private Notification(UUID id, UUID userId, String title, String message, Instant createdAt, Instant readAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.title = requireText(title, "title");
        this.message = requireText(message, "message");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.readAt = readAt;
    }

    /** A new, unread notification. */
    public static Notification create(UUID userId, String title, String message, Instant now) {
        return new Notification(UUID.randomUUID(), userId, title, message, now, null);
    }

    /** Rebuilds a notification from storage. */
    public static Notification reconstitute(
            UUID id, UUID userId, String title, String message, Instant createdAt, Instant readAt) {
        return new Notification(id, userId, title, message, createdAt, readAt);
    }

    /** Marks it read; reading it again keeps the first read time. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = Objects.requireNonNull(now, "now");
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean belongsTo(UUID userId) {
        return this.userId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Optional<Instant> getReadAt() {
        return Optional.ofNullable(readAt);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
