package com.maxime.help.msnotification.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository over {@link RecipientJpaEntity}. Package-private: only the adapter uses it. */
interface SpringDataRecipientJpaRepository extends JpaRepository<RecipientJpaEntity, UUID> {}
