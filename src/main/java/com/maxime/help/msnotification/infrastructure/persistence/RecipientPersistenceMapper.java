package com.maxime.help.msnotification.infrastructure.persistence;

import com.maxime.help.msnotification.domain.model.NotificationPreferences;
import com.maxime.help.msnotification.domain.model.Recipient;
import org.springframework.stereotype.Component;

/** Maps between {@link Recipient} and {@link RecipientJpaEntity}. */
@Component
class RecipientPersistenceMapper {

    Recipient toDomain(RecipientJpaEntity entity) {
        return Recipient.reconstitute(
                entity.getUserId(),
                entity.getEmail(),
                new NotificationPreferences(
                        entity.isEnabled(),
                        entity.isEventActivity(),
                        entity.isEventMessages(),
                        entity.isDocuments(),
                        entity.isDeadlines(),
                        entity.isNearbyEvents(),
                        entity.isJudgments()));
    }

    RecipientJpaEntity toEntity(Recipient recipient) {
        RecipientJpaEntity entity = new RecipientJpaEntity();
        entity.setUserId(recipient.getUserId());
        updateEntity(entity, recipient);
        return entity;
    }

    void updateEntity(RecipientJpaEntity entity, Recipient recipient) {
        NotificationPreferences preferences = recipient.getPreferences();
        entity.setEmail(recipient.getEmail().orElse(null));
        entity.setEnabled(preferences.enabled());
        entity.setEventActivity(preferences.eventActivity());
        entity.setEventMessages(preferences.eventMessages());
        entity.setDocuments(preferences.documents());
        entity.setDeadlines(preferences.deadlines());
        entity.setNearbyEvents(preferences.nearbyEvents());
        entity.setJudgments(preferences.judgments());
    }
}
