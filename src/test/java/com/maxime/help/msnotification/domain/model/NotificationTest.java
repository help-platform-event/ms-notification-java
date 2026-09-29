package com.maxime.help.msnotification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

    @Test
    void aNewNotification_isUnread() {
        Notification notification = Notification.create(USER_ID, "Title", "Message", NOW);

        assertThat(notification.isRead()).isFalse();
        assertThat(notification.belongsTo(USER_ID)).isTrue();
        assertThat(notification.belongsTo(UUID.randomUUID())).isFalse();
    }

    @Test
    void markRead_keepsTheFirstReadTime() {
        Notification notification = Notification.create(USER_ID, "Title", "Message", NOW);

        notification.markRead(NOW.plusSeconds(60));
        notification.markRead(NOW.plusSeconds(120));

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).contains(NOW.plusSeconds(60));
    }

    @Test
    void aBlankTitle_isRejected() {
        assertThatThrownBy(() -> Notification.create(USER_ID, " ", "Message", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
