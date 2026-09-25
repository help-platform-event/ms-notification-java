package com.maxime.help.msnotification.infrastructure.persistence;

import com.maxime.help.msnotification.domain.model.Recipient;
import com.maxime.help.msnotification.domain.port.out.RecipientRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adapts Spring Data JPA to the {@link RecipientRepository} port. */
@Component
class RecipientRepositoryAdapter implements RecipientRepository {

    private final SpringDataRecipientJpaRepository jpa;
    private final RecipientPersistenceMapper mapper;

    RecipientRepositoryAdapter(SpringDataRecipientJpaRepository jpa, RecipientPersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<Recipient> findByUserId(UUID userId) {
        return jpa.findById(userId).map(mapper::toDomain);
    }

    @Override
    public Recipient save(Recipient recipient) {
        RecipientJpaEntity entity = jpa.findById(recipient.getUserId())
                .map(existing -> {
                    mapper.updateEntity(existing, recipient);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(recipient));
        return mapper.toDomain(jpa.save(entity));
    }
}
