package com.maxime.help.msnotification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maxime.help.msnotification.domain.model.Notification;
import com.maxime.help.msnotification.domain.port.out.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T09:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private NotificationRepository notificationRepository;
    @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks private NotificationQueryService service;

    @Test
    void markRead_marksTheUsersOwnNotification() {
        Notification mine = Notification.create(USER_ID, "Title", "Message", NOW.minusSeconds(60));
        when(notificationRepository.findById(mine.getId())).thenReturn(Optional.of(mine));

        assertThat(service.markRead(USER_ID, mine.getId())).isTrue();

        assertThat(mine.getReadAt()).contains(NOW);
        verify(notificationRepository).save(mine);
    }

    @Test
    void markRead_refusesSomeoneElsesNotification_asIfItDidNotExist() {
        Notification theirs = Notification.create(UUID.randomUUID(), "Title", "Message", NOW);
        when(notificationRepository.findById(theirs.getId())).thenReturn(Optional.of(theirs));

        assertThat(service.markRead(USER_ID, theirs.getId())).isFalse();

        assertThat(theirs.isRead()).isFalse();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void latest_neverAsksForANegativePage() {
        service.latest(USER_ID, -3);

        verify(notificationRepository).findLatestByUserId(USER_ID, 0, NotificationQueryService.PAGE_SIZE);
    }
}
