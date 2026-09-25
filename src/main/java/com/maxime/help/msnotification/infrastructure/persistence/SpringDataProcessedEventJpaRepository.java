package com.maxime.help.msnotification.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository over {@link ProcessedEventJpaEntity}. Package-private: only the adapter uses it. */
interface SpringDataProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, UUID> {}
