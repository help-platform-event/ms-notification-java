package com.maxime.help.msnotification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipientTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void registered_hasEmailAndDefaultPreferences() {
        Recipient recipient = Recipient.registered(USER_ID, "alice@example.com");

        assertThat(recipient.getEmail()).contains("alice@example.com");
        assertThat(recipient.getPreferences()).isEqualTo(NotificationPreferences.defaults());
    }

    @Test
    void settingsFirst_thenRegistration_keepsThePreferencesAndAddsTheEmail() {
        NotificationPreferences muted = new NotificationPreferences(false, false, false, false, false, false, false);
        Recipient recipient = Recipient.withPreferencesOnly(USER_ID, muted);
        assertThat(recipient.getEmail()).isEmpty();

        recipient.registerEmail("alice@example.com");

        assertThat(recipient.getEmail()).contains("alice@example.com");
        assertThat(recipient.getPreferences()).isEqualTo(muted);
    }

    @Test
    void rejectsABlankEmail() {
        assertThatThrownBy(() -> Recipient.registered(USER_ID, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
