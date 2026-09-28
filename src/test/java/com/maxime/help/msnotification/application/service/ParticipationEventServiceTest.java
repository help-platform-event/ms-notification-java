package com.maxime.help.msnotification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.maxime.help.msnotification.domain.model.Email;
import com.maxime.help.msnotification.domain.model.NotificationPreferences;
import com.maxime.help.msnotification.domain.model.Recipient;
import com.maxime.help.msnotification.domain.port.out.EmailSender;
import com.maxime.help.msnotification.domain.port.out.ProcessedEventRepository;
import com.maxime.help.msnotification.domain.port.out.RecipientRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

/** Pure application-service test: every port is mocked. */
@ExtendWith(MockitoExtension.class)
class ParticipationEventServiceTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final UUID VOLUNTEER_ID = UUID.randomUUID();
    // 08:00 UTC = 10h00 in Paris (summer time).
    private static final Instant SLOT_START = Instant.parse("2026-10-03T08:00:00Z");

    /** The volunteer asked to join the organizer's slot. */
    private static final ParticipationActivity REQUESTED =
            new ParticipationActivity(42, ORGANIZER_ID, VOLUNTEER_ID, "Clean-up day", SLOT_START);

    /** The organizer decided on the volunteer's request. */
    private static final ParticipationActivity DECIDED =
            new ParticipationActivity(42, VOLUNTEER_ID, ORGANIZER_ID, "Clean-up day", SLOT_START);

    @Mock private RecipientRepository recipientRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private EmailSender emailSender;

    @InjectMocks private ParticipationEventService service;

    @Test
    void requested_emailsTheOrganizer_withTheSlotInParisTime() {
        when(recipientRepository.findByUserId(ORGANIZER_ID))
                .thenReturn(Optional.of(Recipient.registered(ORGANIZER_ID, "orga@example.com")));

        service.onParticipationRequested(EVENT_ID, REQUESTED);

        Email email = capturedEmail();
        assertThat(email.to()).isEqualTo("orga@example.com");
        assertThat(email.subject()).isEqualTo("Nouvelle demande de participation : Clean-up day");
        assertThat(email.body()).contains("samedi 3 octobre 2026 à 10h00", "« Clean-up day »");
        verify(processedEventRepository).markProcessed(EVENT_ID);
    }

    @Test
    void decided_tellsTheParticipantWhetherTheyWereAccepted() {
        when(recipientRepository.findByUserId(VOLUNTEER_ID))
                .thenReturn(Optional.of(Recipient.registered(VOLUNTEER_ID, "vol@example.com")));

        service.onParticipationDecided(EVENT_ID, DECIDED, true);
        assertThat(capturedEmail().subject()).isEqualTo("Participation acceptée : Clean-up day");
    }

    @Test
    void aLineBreakInTheEventTitle_neverReachesTheSubject() {
        when(recipientRepository.findByUserId(VOLUNTEER_ID))
                .thenReturn(Optional.of(Recipient.registered(VOLUNTEER_ID, "vol@example.com")));
        ParticipationActivity injected = new ParticipationActivity(
                42, VOLUNTEER_ID, ORGANIZER_ID, "Fête\r\nBcc: victim@example.com", SLOT_START);

        service.onParticipationDecided(EVENT_ID, injected, true);

        assertThat(capturedEmail().subject())
                .isEqualTo("Participation acceptée : Fête Bcc: victim@example.com")
                .doesNotContain("\r", "\n");
    }

    @Test
    void decided_rejected_usesTheRejectionText() {
        when(recipientRepository.findByUserId(VOLUNTEER_ID))
                .thenReturn(Optional.of(Recipient.registered(VOLUNTEER_ID, "vol@example.com")));

        service.onParticipationDecided(EVENT_ID, DECIDED, false);

        Email email = capturedEmail();
        assertThat(email.subject()).isEqualTo("Participation refusée : Clean-up day");
        assertThat(email.body()).contains("n'a pas été retenue");
    }

    @Test
    void eventActivityMuted_sendsNothing_butMarksTheEventProcessed() {
        Recipient muted = Recipient.registered(VOLUNTEER_ID, "vol@example.com");
        muted.changePreferences(new NotificationPreferences(true, false, true, true, true, true, true));
        when(recipientRepository.findByUserId(VOLUNTEER_ID)).thenReturn(Optional.of(muted));

        service.onParticipationDecided(EVENT_ID, DECIDED, true);

        verify(emailSender, never()).send(any());
        verify(processedEventRepository).markProcessed(EVENT_ID);
    }

    @Test
    void masterSwitchOff_sendsNothing() {
        Recipient muted = Recipient.registered(VOLUNTEER_ID, "vol@example.com");
        muted.changePreferences(new NotificationPreferences(false, true, true, true, true, true, true));
        when(recipientRepository.findByUserId(VOLUNTEER_ID)).thenReturn(Optional.of(muted));

        service.onParticipationDecided(EVENT_ID, DECIDED, true);

        verify(emailSender, never()).send(any());
    }

    @Test
    void unknownRecipient_sendsNothing_butMarksTheEventProcessed() {
        when(recipientRepository.findByUserId(ORGANIZER_ID)).thenReturn(Optional.empty());

        service.onParticipationRequested(EVENT_ID, REQUESTED);

        verify(emailSender, never()).send(any());
        verify(processedEventRepository).markProcessed(EVENT_ID);
    }

    @Test
    void recipientWithoutKnownEmail_sendsNothing() {
        when(recipientRepository.findByUserId(ORGANIZER_ID))
                .thenReturn(Optional.of(Recipient.withPreferencesOnly(ORGANIZER_ID, NotificationPreferences.defaults())));

        service.onParticipationRequested(EVENT_ID, REQUESTED);

        verify(emailSender, never()).send(any());
        verify(processedEventRepository).markProcessed(EVENT_ID);
    }

    @Test
    void anOrganizerJoiningTheirOwnSlot_isNotNotified() {
        ParticipationActivity self = new ParticipationActivity(42, ORGANIZER_ID, ORGANIZER_ID, "Clean-up day", SLOT_START);

        service.onParticipationRequested(EVENT_ID, self);

        verifyNoInteractions(recipientRepository, emailSender);
        verify(processedEventRepository).markProcessed(EVENT_ID);
    }

    @Test
    void aFailedSend_propagates_soTheEventIsNotMarkedProcessedAndWillBeRetried() {
        when(recipientRepository.findByUserId(VOLUNTEER_ID))
                .thenReturn(Optional.of(Recipient.registered(VOLUNTEER_ID, "vol@example.com")));
        doThrow(new MailSendException("SMTP down")).when(emailSender).send(any());

        assertThatThrownBy(() -> service.onParticipationDecided(EVENT_ID, DECIDED, true))
                .isInstanceOf(MailSendException.class);
        verify(processedEventRepository, never()).markProcessed(any());
    }

    @Test
    void anAlreadyProcessedEvent_sendsNothing() {
        when(processedEventRepository.isProcessed(EVENT_ID)).thenReturn(true);

        service.onParticipationRequested(EVENT_ID, REQUESTED);
        service.onParticipationDecided(EVENT_ID, DECIDED, true);

        verifyNoInteractions(recipientRepository, emailSender);
        verify(processedEventRepository, never()).markProcessed(any());
    }

    private Email capturedEmail() {
        ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
        verify(emailSender).send(email.capture());
        return email.getValue();
    }
}
