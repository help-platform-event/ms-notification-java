package com.maxime.help.msnotification.application.service;

import com.maxime.help.msnotification.domain.model.Email;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Texts of the emails this service sends. Plain text for now (HTML templates are out of v1). */
final class EmailContents {

    /**
     * Slot times travel as UTC instants; users read them in French local time ("samedi 3 octobre
     * 2026 à 10h00"). A single zone is enough while H.E.L.P only runs in France.
     */
    private static final DateTimeFormatter SLOT_DATE = DateTimeFormatter.ofPattern(
                    "EEEE d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH)
            .withZone(ZoneId.of("Europe/Paris"));

    private EmailContents() {}

    static Email welcome(String to) {
        return new Email(
                to,
                "Bienvenue sur H.E.L.P",
                """
                Bonjour,

                Votre compte H.E.L.P est créé. Vous pouvez dès maintenant vous inscrire à des \
                créneaux ou organiser vos propres événements.

                À bientôt,
                L'équipe H.E.L.P""");
    }

    static Email passwordChanged(String to) {
        return new Email(
                to,
                "Votre mot de passe a été modifié",
                """
                Bonjour,

                Le mot de passe de votre compte H.E.L.P vient d'être modifié.
                Si vous n'êtes pas à l'origine de ce changement, contactez-nous immédiatement.

                L'équipe H.E.L.P""");
    }

    static Email participationRequested(String to, ParticipationActivity activity) {
        return new Email(
                to,
                "Nouvelle demande de participation : " + oneLine(activity.eventTitle()),
                """
                Bonjour,

                Un bénévole demande à participer au créneau du %s de votre événement « %s ».
                Connectez-vous à H.E.L.P pour accepter ou refuser sa demande.

                L'équipe H.E.L.P""".formatted(SLOT_DATE.format(activity.slotStartAt()), activity.eventTitle()));
    }

    static Email participationDecided(String to, ParticipationActivity activity, boolean accepted) {
        String slot = SLOT_DATE.format(activity.slotStartAt());
        String title = activity.eventTitle();
        return new Email(
                to,
                (accepted ? "Participation acceptée : " : "Participation refusée : ") + oneLine(title),
                accepted
                        ? """
                        Bonjour,

                        Bonne nouvelle : votre participation au créneau du %s de l'événement « %s » \
                        a été acceptée. Merci pour votre engagement !

                        L'équipe H.E.L.P""".formatted(slot, title)
                        : """
                        Bonjour,

                        Votre participation au créneau du %s de l'événement « %s » n'a pas été \
                        retenue par l'organisateur. D'autres créneaux vous attendent sur H.E.L.P.

                        L'équipe H.E.L.P""".formatted(slot, title));
    }

    /**
     * Event titles are typed by users. A line break inside an email header could be used to inject
     * extra headers, so the subject gets the title on a single line.
     */
    private static String oneLine(String text) {
        return text.replaceAll("[\\r\\n]+", " ");
    }
}
