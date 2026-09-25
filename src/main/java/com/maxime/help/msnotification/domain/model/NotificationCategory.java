package com.maxime.help.msnotification.domain.model;

/**
 * What a notification is about. The first two are transactional (account lifecycle, security):
 * always delivered, whatever the user's preferences. The others mirror the per-category switches
 * of the user's notification settings (owned by ms-auth).
 */
public enum NotificationCategory {
    ACCOUNT(true),
    SECURITY(true),
    EVENT_ACTIVITY(false),
    EVENT_MESSAGES(false),
    DOCUMENTS(false),
    DEADLINES(false),
    NEARBY_EVENTS(false),
    JUDGMENTS(false);

    private final boolean transactional;

    NotificationCategory(boolean transactional) {
        this.transactional = transactional;
    }

    public boolean isTransactional() {
        return transactional;
    }
}
