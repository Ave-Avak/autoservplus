package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.service.dto.FichierAgenda;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Export d un rendez-vous confirme vers l agenda du membre (F38, RFC 5545).
 *
 * <p>Perimetre V1 : un <b>fichier</b> par rendez-vous. L URL d abonnement recurrent
 * evoquee au cahier des charges est renvoyee en V2. Elle suppose un jeton d acces
 * porte par l adresse elle-meme, puisqu un client de calendrier ne s authentifie
 * pas : c est un sujet de securite entier, pas une variante du meme ecran.</p>
 *
 * <p><b>Confirme seulement.</b> Une demande en attente n a pas de date arretee : le
 * garage peut encore la refuser ou proposer autre chose. La deposer dans l agenda
 * du membre l installerait comme un fait, et rien ne viendrait l en retirer : le
 * fichier vit chez lui, hors de portee du site. Un rendez-vous non confirme est
 * donc traite comme un fichier qui n existe pas ({@code 404}), pas comme un acces
 * refuse : ce n est pas une question de droits, le membre est bien chez lui.</p>
 */
@Service
public class ExportAgendaService {

    private final RdvService rdvs;
    private final RedactionAgenda redaction;

    public ExportAgendaService(RdvService rdvs, RedactionAgenda redaction) {
        this.rdvs = rdvs;
        this.redaction = redaction;
    }

    /**
     * Fichier d agenda du rendez-vous demande, pour le membre identifie.
     *
     * <p>L appartenance est verifiee par {@link RdvService#rdvDuMembre} : reference
     * inconnue et rendez-vous d autrui produisent la meme exception, donc la meme
     * reponse. Le controle n est pas reecrit ici : le dupliquer serait le laisser
     * diverger.</p>
     */
    @Transactional(readOnly = true)
    public FichierAgenda pourLeMembre(UUID reference, String email, Locale langue) {
        Rdv rdv = rdvs.rdvDuMembre(reference, email);
        if (rdv.getStatut() != StatutRdv.CONFIRME) {
            throw new RessourceIntrouvableException("Agenda", reference);
        }
        return redaction.pour(rdv, langue);
    }

    /** Fichier joint au courriel de confirmation, rédigé par {@link RedactionAgenda}. */
    public FichierAgenda pourLeCourriel(Rdv rdv) {
        return redaction.pourLeCourriel(rdv);
    }
}
