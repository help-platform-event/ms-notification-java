package com.maxime.help.msnotification.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire format of ms-auth's {@code auth.user.registered}. Our own copy of the contract, not a
 * shared class: only the fields this service needs, and unknown ones are ignored so ms-auth can add
 * fields without breaking us.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserRegisteredMessage(UUID eventId, Instant occurredAt, UUID userId, String email) {}
