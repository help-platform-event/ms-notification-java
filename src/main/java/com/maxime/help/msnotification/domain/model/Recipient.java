package com.maxime.help.msnotification.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Someone notifications can be sent to: this service's own copy (projection) of a user, built from
 * ms-auth's events rather than by calling ms-auth. The email comes from the registration event and
 * the preferences from the settings events; either may arrive first, so the email can be unknown
 * for a while.
 */
public final class Recipient {

    private final UUID userId;
    private String email;
    private NotificationPreferences preferences;

    private Recipient(UUID userId, String email, NotificationPreferences preferences) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.email = email;
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    /** A user just registered: known email, default preferences. */
    public static Recipient registered(UUID userId, String email) {
        return new Recipient(userId, requireEmail(email), NotificationPreferences.defaults());
    }

    /** Settings arrived before (or without) the registration event: email still unknown. */
    public static Recipient withPreferencesOnly(UUID userId, NotificationPreferences preferences) {
        return new Recipient(userId, null, preferences);
    }

    /** Rebuilds a recipient from storage. */
    public static Recipient reconstitute(UUID userId, String email, NotificationPreferences preferences) {
        return new Recipient(userId, email, preferences);
    }

    public void registerEmail(String email) {
        this.email = requireEmail(email);
    }

    public void changePreferences(NotificationPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    public boolean accepts(NotificationCategory category) {
        return preferences.allows(category);
    }

    public UUID getUserId() {
        return userId;
    }

    public Optional<String> getEmail() {
        return Optional.ofNullable(email);
    }

    public NotificationPreferences getPreferences() {
        return preferences;
    }

    private static String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return email;
    }
}
