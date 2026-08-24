package be.autoservplus.reservation.service.support;

import java.time.Duration;
import java.time.Instant;

/**
 * Un evenement de calendrier, avant sa mise en forme iCalendar.
 *
 * <p>Volontairement sans aucun vocabulaire de rendez-vous : ce type decrit ce
 * qu attend la RFC 5545, pas ce que sait le garage. C est {@code ExportAgendaService}
 * qui traduit un {@code Rdv} en evenement — la separation permet de tester le
 * redacteur sur des valeurs limites (guillemets, virgules, resume tres long) sans
 * fabriquer un rendez-vous complet.</p>
 *
 * @param uid          identifiant stable de l evenement. <b>Stable</b> est le mot
 *                     important : un client de calendrier reconnait a l UID qu un
 *                     second fichier decrit le meme rendez-vous et le met a jour au
 *                     lieu de le dupliquer. Un identifiant tire au hasard a chaque
 *                     telechargement remplirait l agenda du membre de doublons.
 * @param horodatage   instant de production du fichier ({@code DTSTAMP}).
 * @param debut        debut de l evenement.
 * @param fin          fin de l evenement.
 * @param resume       {@code SUMMARY}, ligne affichee dans l agenda.
 * @param lieu         {@code LOCATION}, adresse sur une ligne.
 * @param description  {@code DESCRIPTION}, corps libre. Les retours a la ligne y
 *                     sont admis et echappes par le redacteur.
 * @param url          {@code URL}, lien de retour vers la fiche. Doit etre absolu :
 *                     le fichier est lu hors du site, un chemin relatif n y designe
 *                     rien.
 * @param rappelAvant  delai du {@code VALARM} avant le debut, ou {@code null} pour
 *                     ne pas en produire.
 */
public record EvenementIcal(
        String uid,
        Instant horodatage,
        Instant debut,
        Instant fin,
        String resume,
        String lieu,
        String description,
        String url,
        Duration rappelAvant) {
}
