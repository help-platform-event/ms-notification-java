package com.maxime.help.msnotification.application.service;

import com.maxime.help.msnotification.domain.model.EmailRequest;
import java.util.UUID;

/** Texts of the emails this service sends. Plain text for now (HTML templates are out of v1). */
final class EmailContents {

    private EmailContents() {}

    static EmailRequest welcome(UUID recipientUserId, String to) {
        return new EmailRequest(
                UUID.randomUUID(),
                recipientUserId,
                to,
                "Bienvenue sur H.E.L.P",
                """
                Bonjour,

                Votre compte H.E.L.P est créé. Vous pouvez dès maintenant vous inscrire à des \
                créneaux ou organiser vos propres événements.

                À bientôt,
                L'équipe H.E.L.P""");
    }

    static EmailRequest passwordChanged(UUID recipientUserId, String to) {
        return new EmailRequest(
                UUID.randomUUID(),
                recipientUserId,
                to,
                "Votre mot de passe a été modifié",
                """
                Bonjour,

                Le mot de passe de votre compte H.E.L.P vient d'être modifié.
                Si vous n'êtes pas à l'origine de ce changement, contactez-nous immédiatement.

                L'équipe H.E.L.P""");
    }
}
