package com.maxime.help.msnotification.domain.port.out;

import com.maxime.help.msnotification.domain.model.Recipient;
import java.util.Optional;
import java.util.UUID;

public interface RecipientRepository {

    Optional<Recipient> findByUserId(UUID userId);

    Recipient save(Recipient recipient);
}
