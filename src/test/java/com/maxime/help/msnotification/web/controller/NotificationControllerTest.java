package com.maxime.help.msnotification.web.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maxime.help.msnotification.application.service.NotificationQueryService;
import com.maxime.help.msnotification.domain.model.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The HTTP layer with the real security rules; the user comes from a (mocked) JWT's subject. */
@WebMvcTest(
        controllers = NotificationController.class,
        properties = "app.auth.jwt.secret=c2FtcGxlLWRldi1vbmx5LXNlY3JldC1kby1ub3QtdXNlLWluLXByb2QtMzJieXRlcyE=")
@ComponentScan(basePackages = "com.maxime.help.msnotification.infrastructure.security")
class NotificationControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired MockMvc mockMvc;

    @MockitoBean NotificationQueryService notificationQueryService;

    @Test
    void withoutAToken_theApiAnswers401() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    @Test
    void unreadCount_isTheTokenSubjects() throws Exception {
        when(notificationQueryService.unreadCount(USER_ID)).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread-count").with(jwt().jwt(j -> j.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void latest_listsTheUsersNotifications() throws Exception {
        Notification notification =
                Notification.create(USER_ID, "Participation acceptée", "Message", Instant.parse("2026-09-28T09:00:00Z"));
        when(notificationQueryService.latest(USER_ID, 0)).thenReturn(List.of(notification));

        mockMvc.perform(get("/api/notifications").with(jwt().jwt(j -> j.subject(USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Participation acceptée"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].createdAt").value("2026-09-28T09:00:00Z"));
    }

    @Test
    void markRead_ofAnUnknownOrForeignNotification_is404() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationQueryService.markRead(USER_ID, id)).thenReturn(false);

        mockMvc.perform(patch("/api/notifications/{id}/read", id).with(jwt().jwt(j -> j.subject(USER_ID.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Notification not found"));
    }

    @Test
    void markRead_ofOwnNotification_is204() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationQueryService.markRead(USER_ID, id)).thenReturn(true);

        mockMvc.perform(patch("/api/notifications/{id}/read", id).with(jwt().jwt(j -> j.subject(USER_ID.toString()))))
                .andExpect(status().isNoContent());
        verify(notificationQueryService).markRead(USER_ID, id);
    }
}
