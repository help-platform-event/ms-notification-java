package com.maxime.help.msnotification.domain.model;

import java.util.Objects;

/** A plain-text email to send. */
public record Email(String to, String subject, String body) {

    public Email {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("to must not be blank");
        }
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
    }
}
