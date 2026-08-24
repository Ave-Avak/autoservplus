package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.service.dto.FichierAgenda;
import be.autoservplus.reservation.service.support.EvenementIcal;
import be.autoservplus.reservation.service.support.RedacteurIcal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Export d un rendez-vous confirme vers l agenda du membre (F38, RFC 5545).
 *
 * <p>Perimetre V1 : un <b>fichier</b> par rendez-vous. L URL d abonnement recurrent
 * evoquee au cahier des charges est renvoyee en V2 — elle suppose un jeton d acces
 * porte par l adresse elle-meme, puisqu un client de calendrier ne s authentifie
 * pas : c est un sujet de securite entier, pas une variante du meme ecran.</p>
 *
 * <p><b>Confirme seulement.</b> Une demande en attente n a pas de date arretee : le
 * garage peut encore la refuser ou proposer autre chose. La deposer dans l agenda
 * du membre l installerait comme un fait, et rien ne viendrait l en retirer — le
 * fichier vit chez lui, hors de portee du site. Un rendez-vous non confirme est
 * donc traite comme un fichier qui n existe pas ({@code 404}), pas comme un acces
 * refuse : ce n est pas une question de droits, le membre est bien chez lui.</p>
 */
@Service
public class ExportAgendaService {

    /** Rappel la veille, exigence du cahier des charges pour F38. */
    private static final Duration RAPPEL = Duration.ofHours(24);

    private final RdvService rdvs;
    private final IdentiteGarage garage;
    private final MessageSource messages;
    private final Clock horloge;
    private final String urlPublique;

    public ExportAgendaService(RdvService rdvs, IdentiteGarage garage, MessageSource messages,
                               Clock horloge,
                               @Value("${autoservplus.url-publique}") String urlPublique) {
        this.rdvs = rdvs;
        this.garage = garage;
        this.messages = messages;
        this.horloge = horloge;
        // Une adresse de base terminee par « / » produirait « //mes-rendez-vous ».
        this.urlPublique = urlPublique.endsWith("/")
                ? urlPublique.substring(0, urlPublique.length() - 1)
                : urlPublique;
    }

    /**
     * Fichier d agenda du rendez-vous demande, pour le membre identifie.
     *
     * <p>L appartenance est verifiee par {@link RdvService#rdvDuMembre} : reference
     * inconnue et rendez-vous d autrui produisent la meme exception, donc la meme
     * reponse. Le controle n est pas reecrit ici — le dupliquer serait le laisser
     * diverger.</p>
     */
    @Transactional(readOnly = true)
    public FichierAgenda pourLeMembre(UUID reference, String email, Locale langue) {
        Rdv rdv = rdvs.rdvDuMembre(reference, email);
        if (rdv.getStatut() != StatutRdv.CONFIRME) {
            throw new RessourceIntrouvableException("Agenda", reference);
        }
        return construire(rdv, langue);
    }

    /**
     * Fichier joint au courriel de confirmation. La langue est celle enregistree au
     * profil du membre et non celle de l administrateur qui confirme : c est le
     * meme choix que pour les factures PDF, et pour la meme raison — le document
     * part chez le membre.
     *
     * <p><b>Deliberement SANS {@code @Transactional}</b>, contrairement a la methode
     * voisine. Le rendez-vous est deja charge par l appelant, dans sa propre
     * transaction d ecriture : ouvrir ici une transaction imbriquee n apporterait
     * rien et couterait cher au premier incident. Une transaction en propagation
     * {@code REQUIRED} rejoint celle de l appelant, et toute exception qui traverse
     * son intercepteur la marque <i>rollback-only</i> — la confirmation du
     * rendez-vous, deja ecrite, echouerait alors au commit sur une
     * {@code UnexpectedRollbackException}, pour un fichier d agenda. Sans
     * annotation, l echec reste un simple echec de methode que l appelant absorbe.
     * C est le meme piege que celui documente pour les notifications par courriel.</p>
     */
    public FichierAgenda pourLeCourriel(Rdv rdv) {
        return construire(rdv, Locale.forLanguageTag(rdv.getMembre().getLangue().name()));
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

    /** Adresse complete du garage sur une ligne, pays compris : le fichier est lu hors du site. */
    private String lieu() {
        return garage.raisonSociale() + ", " + garage.adresseLisible() + ", " + garage.pays();
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
