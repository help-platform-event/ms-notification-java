package com.maxime.help.msnotification.infrastructure.messaging;

import com.maxime.help.msnotification.application.service.ParticipationActivity;
import com.maxime.help.msnotification.application.service.ParticipationEventService;
import com.maxime.help.msnotification.infrastructure.messaging.dto.ParticipationDecidedMessage;
import com.maxime.help.msnotification.infrastructure.messaging.dto.ParticipationRequestedMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the Gateway's participation events. Same consumer group as {@link AuthEventListener}: a
 * group is per service, not per topic, and Kafka spreads every subscribed topic's partitions across
 * the group's instances.
 */
@Component
class ParticipationEventListener {

    private final ParticipationEventService participationEventService;

    ParticipationEventListener(ParticipationEventService participationEventService) {
        this.participationEventService = participationEventService;
    }

    @KafkaListener(topics = NotificationTopics.PARTICIPATION_REQUESTED)
    void onParticipationRequested(ParticipationRequestedMessage message) {
        participationEventService.onParticipationRequested(
                message.eventId(),
                new ParticipationActivity(
                        message.participationId(),
                        message.recipientUserId(),
                        message.actorUserId(),
                        message.event().title(),
                        message.slot().startAt()));
    }

    @KafkaListener(topics = NotificationTopics.PARTICIPATION_DECIDED)
    void onParticipationDecided(ParticipationDecidedMessage message) {
        participationEventService.onParticipationDecided(
                message.eventId(),
                new ParticipationActivity(
                        message.participationId(),
                        message.recipientUserId(),
                        message.actorUserId(),
                        message.event().title(),
                        message.slot().startAt()),
                message.status() == ParticipationDecidedMessage.Decision.ACCEPTED);
    }
}
