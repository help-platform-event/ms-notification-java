package com.maxime.help.msnotification.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/** Wire format of ms-auth's {@code auth.password.changed}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PasswordChangedMessage(UUID eventId, Instant occurredAt, UUID userId) {}
