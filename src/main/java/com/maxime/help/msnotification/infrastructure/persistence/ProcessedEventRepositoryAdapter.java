package com.maxime.help.msnotification.infrastructure.persistence;

import com.maxime.help.msnotification.domain.port.out.ProcessedEventRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adapts Spring Data JPA to the {@link ProcessedEventRepository} port. */
@Component
class ProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final SpringDataProcessedEventJpaRepository jpa;
    private final Clock clock;

    ProcessedEventRepositoryAdapter(SpringDataProcessedEventJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    public boolean isProcessed(UUID eventId) {
        return jpa.existsById(eventId);
    }

    @Override
    public void markProcessed(UUID eventId) {
        jpa.save(new ProcessedEventJpaEntity(eventId, clock.instant()));
    }
}
