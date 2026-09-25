package com.maxime.help.msnotification.domain.port.out;

import java.util.UUID;

/**
 * Remembers which messages were already handled. Kafka delivers at least once (a message can come
 * again after a crash, a rebalance or an offset reset), so every handler checks this first.
 */
public interface ProcessedEventRepository {

    boolean isProcessed(UUID eventId);

    /** Records the message as handled; call it in the same transaction as the handling itself. */
    void markProcessed(UUID eventId);
}
