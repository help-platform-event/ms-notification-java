package com.maxime.help.msnotification.domain.port.out;

import com.maxime.help.msnotification.domain.model.Notification;

public interface NotificationPusher {

    /**
     * Pushes a new in-app notification to its recipient's open sessions, if any. Inside a
     * transaction, only once it commits: a rolled-back notification is never shown.
     */
    void push(Notification notification);
}
