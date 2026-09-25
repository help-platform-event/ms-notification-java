package com.maxime.help.msnotification.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * An email to deliver. {@code requestId} identifies the request itself, so a redelivered request
 * is recognised and not sent twice.
 */
public record EmailRequest(UUID requestId, UUID recipientUserId, String to, String subject, String body) {

    public EmailRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(recipientUserId, "recipientUserId");
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("to must not be blank");
        }
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
    }
}
