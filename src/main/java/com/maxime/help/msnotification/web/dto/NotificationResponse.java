package com.maxime.help.msnotification.web.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, String title, String message, Instant createdAt, boolean read) {}
