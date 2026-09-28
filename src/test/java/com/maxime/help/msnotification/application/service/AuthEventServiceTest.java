package com.maxime.help.msnotification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maxime.help.msnotification.domain.model.Email;
import com.maxime.help.msnotification.domain.model.NotificationPreferences;
import com.maxime.help.msnotification.domain.model.Recipient;
import com.maxime.help.msnotification.domain.port.out.EmailSender;
import com.maxime.help.msnotification.domain.port.out.ProcessedEventRepository;
import com.maxime.help.msnotification.domain.port.out.RecipientRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Pure application-service test: every port is mocked. */
@ExtendWith(MockitoExtension.class)
class AuthEventServiceTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final NotificationPreferences MUTED =
            new NotificationPreferences(false, false, false, false, false, false, false);

    @Mock private RecipientRepository recipientRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private EmailSender emailSender;

    @InjectMocks private AuthEventService service;

    @Test
    void userRegistered_createsTheRecipientAndRequestsAWelcomeEmail() {
        when(recipientRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        service.onUserRegistered(EVENT_ID, USER_ID, "alice@example.com");

        ArgumentCaptor<Recipient> saved = ArgumentCaptor.forClass(Recipient.class);
        verify(recipientRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).contains("alice@example.com");
        ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().to()).isEqualTo("alice@example.com");
        assertThat(email.getValue().subject()).contains("Bienvenue");
        verify(processedEventRepository).markProcessed(EVENT_ID);
    }

    @Test
    void userRegistered_afterSettings_keepsThePreferencesAlreadyReceived() {
        when(recipientRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(Recipient.withPreferencesOnly(USER_ID, MUTED)));

        service.onUserRegistered(EVENT_ID, USER_ID, "alice@example.com");

        ArgumentCaptor<Recipient> saved = ArgumentCaptor.forClass(Recipient.class);
        verify(recipientRepository).save(saved.capture());
        assertThat(saved.getValue().getPreferences()).isEqualTo(MUTED);
        assertThat(saved.getValue().getEmail()).contains("alice@example.com");
    }

    @Test
    void anAlreadyProcessedEvent_changesNothingAndSendsNothing() {
        when(processedEventRepository.isProcessed(EVENT_ID)).thenReturn(true);

        service.onUserRegistered(EVENT_ID, USER_ID, "alice@example.com");
        service.onUserSettingsChanged(EVENT_ID, USER_ID, MUTED);
        service.onPasswordChanged(EVENT_ID, USER_ID);

        verify(recipientRepository, never()).save(any());
        verify(emailSender, never()).send(any());
        verify(processedEventRepository, never()).markProcessed(any());
    }

    @Test
    void settingsChanged_forAnUnknownUser_storesThePreferencesWithoutEmail() {
        when(recipientRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        service.onUserSettingsChanged(EVENT_ID, USER_ID, MUTED);

        ArgumentCaptor<Recipient> saved = ArgumentCaptor.forClass(Recipient.class);
        verify(recipientRepository).save(saved.capture());
        assertThat(saved.getValue().getPreferences()).isEqualTo(MUTED);
        assertThat(saved.getValue().getEmail()).isEmpty();
        verify(emailSender, never()).send(any());
    }

    @Test
    void passwordChanged_requestsASecurityEmail_evenWhenNotificationsAreMuted() {
        Recipient muted = Recipient.registered(USER_ID, "alice@example.com");
        muted.changePreferences(MUTED);
        when(recipientRepository.findByUserId(USER_ID)).thenReturn(Optional.of(muted));

        service.onPasswordChanged(EVENT_ID, USER_ID);

        ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().subject()).contains("mot de passe");
        verify(processedEventRepository).markProcessed(EVENT_ID);
    }

    @Test
    void passwordChanged_withoutAKnownEmail_isSkippedButStillMarkedProcessed() {
        when(recipientRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        service.onPasswordChanged(EVENT_ID, USER_ID);

        verify(emailSender, never()).send(any());
        verify(processedEventRepository).markProcessed(EVENT_ID);
    }
}
