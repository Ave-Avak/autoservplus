package be.autoservplus.reservation.service;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.service.dto.FichierAgenda;
import be.autoservplus.reservation.service.support.EvenementIcal;
import be.autoservplus.reservation.service.support.RedacteurIcal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rédaction du fichier d'agenda d'un rendez-vous (F38, RFC 5545), à partir d'un
 * rendez-vous déjà chargé.
 *
 * <p>Séparée d'{@link ExportAgendaService} parce que l'accusé d'une demande confirmée
 * d'office en a besoin : l'accusé part de la réservation, et {@code ExportAgendaService}
 * dépend du service de réservation pour vérifier l'appartenance. La rédaction, elle, ne
 * dépend que du garage et des messages.</p>
 *
 * <p><b>Délibérément sans {@code @Transactional}</b> : elle est appelée dans la
 * transaction d'écriture d'une confirmation, et une transaction rejointe marquerait la
 * confirmation <i>rollback-only</i> au premier échec de rédaction. Sans annotation,
 * l'échec reste un échec de méthode que l'appelant absorbe.</p>
 */
@Component
public class RedactionAgenda {

    /** Rappel la veille, exigence du cahier des charges pour F38. */
    private static final Duration RAPPEL = Duration.ofHours(24);

    private final IdentiteGarage garage;
    private final MessageSource messages;
    private final Clock horloge;
    private final String urlPublique;

    public RedactionAgenda(IdentiteGarage garage, MessageSource messages, Clock horloge,
                           @Value("${autoservplus.url-publique}") String urlPublique) {
        this.garage = garage;
        this.messages = messages;
        this.horloge = horloge;
        // Une adresse de base terminee par « / » produirait « //mes-rendez-vous ».
        this.urlPublique = urlPublique.endsWith("/")
                ? urlPublique.substring(0, urlPublique.length() - 1)
                : urlPublique;
    }

    /**
     * Fichier joint à un courriel. La langue est celle enregistrée au profil du membre
     * et non celle de l'administrateur qui confirme : le document part chez le membre.
     */
    public FichierAgenda pourLeCourriel(Rdv rdv) {
        return construire(rdv, Locale.forLanguageTag(rdv.getMembre().getLangue().name()));
    }

    FichierAgenda pour(Rdv rdv, Locale langue) {
        return construire(rdv, langue);
    }

    private FichierAgenda construire(Rdv rdv, Locale langue) {
        String resume = message("rdv.agenda.resume", langue, garage.raisonSociale());
        String lien = urlPublique + "/mes-rendez-vous/" + rdv.getReference();

        String description = String.join("\n",
                message("rdv.agenda.prestations", langue, prestations(rdv)),
                message("rdv.agenda.vehicule", langue, vehicule(rdv)),
                message("rdv.agenda.lien", langue, lien));

        EvenementIcal evenement = new EvenementIcal(
                // L UID est la reference du rendez-vous, deja unique et deja stable :
                // reexporter le meme rendez-vous met a jour l entree existante au lieu
                // d en creer une seconde.
                rdv.getReference() + "@autoservplus",
                horloge.instant(),
                rdv.getDebut(),
                rdv.getFin(),
                resume,
                lieu(),
                description,
                lien,
                RAPPEL);

        return new FichierAgenda(nomFichier(rdv, langue), RedacteurIcal.calendrier(evenement));
    }

    private String prestations(Rdv rdv) {
        List<String> libelles = rdv.getLignes().stream()
                .map(ligne -> ligne.getPrestation().getLibelle())
                .toList();
        return String.join(", ", libelles);
    }

    private String vehicule(Rdv rdv) {
        var vehicule = rdv.getVehicule();
        return vehicule.getMarque() + " " + vehicule.getModele() + " (" + vehicule.getPlaque() + ")";
    }

    private String lieu() {
        return lieu(garage);
    }

    /**
     * Adresse complète du garage sur une ligne, pays compris : le fichier est lu hors du
     * site. Une partie absente disparaît avec son séparateur, au lieu de laisser une
     * virgule suivie d'un champ vide.
     */
    static String lieu(IdentiteGarage garage) {
        return joindre(", ", garage.raisonSociale(),
                joindre(" ", garage.rue(), garage.numeroRue()),
                joindre(" ", garage.codePostal(), garage.localite()),
                garage.pays());
    }

    private static String joindre(String separateur, String... parties) {
        return Stream.of(parties)
                .filter(partie -> partie != null && !partie.isBlank())
                .map(String::strip)
                .collect(Collectors.joining(separateur));
    }

    /**
     * Nom du fichier propose au telechargement.
     *
     * <p>Il est <b>assaini</b> et pas seulement traduit : la valeur finit dans un
     * en-tete {@code Content-Disposition}, ou un retour a la ligne ou un guillemet
     * permettrait d y injecter une directive. Le numero de rendez-vous est deja sur
     * un alphabet sur : le filtre protege contre une traduction future maladroite,
     * pas contre les donnees d aujourd hui.</p>
     */
    private String nomFichier(Rdv rdv, Locale langue) {
        String propose = message("rdv.agenda.fichier", langue, rdv.getNumero());
        String assaini = propose.replaceAll("[^A-Za-z0-9._-]", "-");
        return assaini.isBlank() ? "rendez-vous.ics" : assaini;
    }

    private String message(String cle, Locale langue, Object... arguments) {
        return messages.getMessage(cle, arguments, langue);
    }
}
