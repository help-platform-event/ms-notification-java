package com.maxime.help.msnotification.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** Wire format of the internal {@code notification.email.requested} topic. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailRequestMessage(UUID requestId, UUID recipientUserId, String to, String subject, String body) {}
