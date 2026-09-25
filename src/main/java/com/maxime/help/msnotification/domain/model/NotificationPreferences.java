package com.maxime.help.msnotification.domain.model;

/**
 * A user's notification preferences, as last published by ms-auth. {@code enabled} is the master
 * switch; the other flags select individual categories. Immutable value object.
 */
public record NotificationPreferences(
        boolean enabled,
        boolean eventActivity,
        boolean eventMessages,
        boolean documents,
        boolean deadlines,
        boolean nearbyEvents,
        boolean judgments) {

    /** Everything on: what a user who never changed their settings gets (same default as ms-auth). */
    public static NotificationPreferences defaults() {
        return new NotificationPreferences(true, true, true, true, true, true, true);
    }

    /**
     * Whether a notification of this category may be sent. Transactional categories always pass;
     * the others need both the master switch and their own switch.
     */
    public boolean allows(NotificationCategory category) {
        if (category.isTransactional()) {
            return true;
        }
        if (!enabled) {
            return false;
        }
        return switch (category) {
            case EVENT_ACTIVITY -> eventActivity;
            case EVENT_MESSAGES -> eventMessages;
            case DOCUMENTS -> documents;
            case DEADLINES -> deadlines;
            case NEARBY_EVENTS -> nearbyEvents;
            case JUDGMENTS -> judgments;
            case ACCOUNT, SECURITY -> true;
        };
    }
}
