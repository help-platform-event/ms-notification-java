package com.maxime.help.msnotification.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.maxime.help.msnotification.domain.model.Notification;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseNotificationStreamsTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final SseNotificationStreams streams = new SseNotificationStreams();

    /** An emitter that records the raw SSE text it's asked to write instead of writing it. */
    private static class RecordingEmitter extends SseEmitter {
        final List<String> sent = new ArrayList<>();
        boolean broken;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (broken) {
                throw new IOException("Broken pipe");
            }
            sent.add(builder.build().stream()
                    .map(part -> String.valueOf(part.getData()))
                    .collect(Collectors.joining()));
        }

        boolean received(String fragment) {
            return sent.stream().anyMatch(text -> text.contains(fragment));
        }
    }

    private static Notification notificationFor(UUID userId) {
        return Notification.create(userId, "Participation acceptée", "Message", Instant.parse("2026-09-29T09:00:00Z"));
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void outsideATransaction_pushesAtOnce_toEveryTabOfTheRecipientOnly() {
        RecordingEmitter tab1 = new RecordingEmitter();
        RecordingEmitter tab2 = new RecordingEmitter();
        RecordingEmitter someoneElse = new RecordingEmitter();
        streams.register(USER_ID, tab1);
        streams.register(USER_ID, tab2);
        streams.register(UUID.randomUUID(), someoneElse);

        streams.push(notificationFor(USER_ID));

        assertThat(tab1.received("event:notification")).isTrue();
        assertThat(tab2.received("Participation acceptée")).isTrue();
        assertThat(someoneElse.sent).isEmpty();
    }

    @Test
    void insideATransaction_waitsForTheCommit() {
        RecordingEmitter tab = new RecordingEmitter();
        streams.register(USER_ID, tab);
        TransactionSynchronizationManager.initSynchronization();

        streams.push(notificationFor(USER_ID));
        assertThat(tab.sent).isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(tab.received("event:notification")).isTrue();
    }

    @Test
    void aRolledBackTransaction_pushesNothing() {
        RecordingEmitter tab = new RecordingEmitter();
        streams.register(USER_ID, tab);
        TransactionSynchronizationManager.initSynchronization();

        streams.push(notificationFor(USER_ID));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(tab.sent).isEmpty();
    }

    @Test
    void aTabThatCantBeWrittenTo_isDropped() {
        RecordingEmitter closedTab = new RecordingEmitter();
        streams.register(USER_ID, closedTab);
        closedTab.broken = true;

        streams.heartbeat();
        closedTab.broken = false;
        streams.push(notificationFor(USER_ID));

        assertThat(closedTab.sent).isEmpty();
    }

    @Test
    void heartbeat_writesAComment_thatClientsIgnore() {
        RecordingEmitter tab = new RecordingEmitter();
        streams.register(USER_ID, tab);

        streams.heartbeat();

        assertThat(tab.sent).containsExactly(":keep-alive\n\n");
    }
}
