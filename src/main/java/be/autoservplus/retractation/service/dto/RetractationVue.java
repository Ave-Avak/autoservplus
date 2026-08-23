package be.autoservplus.retractation.service.dto;

import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.domain.StatutDemandeAnnulation;

import java.util.UUID;

/**
 * Etat de la retractation pour une commande, tel que l ecran du membre en a besoin :
 * peut-il demander ? une demande est-elle en cours ? une note de credit est-elle
 * disponible ?
 *
 * <p>Une seule vue par commande, portant la <b>derniere</b> demande. Un membre peut
 * en avoir plusieurs — un refus n eteint pas son droit — mais l ecran ne montre que
 * la situation actuelle. L historique complet reste en base.</p>
 *
 * <p>{@code statutDemande} est expose brut ({@code null} s il n y a jamais eu de
 * demande) : le libelle affiche est une cle i18n construite dans le gabarit, aucune
 * chaine visible n est fabriquee ici — convention du projet.</p>
 */
public record RetractationVue(
        UUID referenceCommande,
        boolean demandable,
        StatutDemandeAnnulation statutDemande,
        UUID referenceAvoir,
        String numeroAvoir) {

    /** Commande sans aucune demande : seule l eligibilite est a dire. */
    public static RetractationVue sansDemande(UUID referenceCommande, boolean demandable) {
        return new RetractationVue(referenceCommande, demandable, null, null, null);
    }

    /**
     * Commande portant une demande. {@code demandable} vaut alors ce que le controle
     * d eligibilite a conclu : faux tant que la demande est pendante, et faux apres
     * une validation (la commande est remboursee), mais potentiellement vrai apres un
     * refus si le delai legal court encore.
     */
    public static RetractationVue avecDemande(DemandeAnnulation demande, boolean demandable) {
        return new RetractationVue(
                demande.getCommande().getReference(),
                demandable,
                demande.getStatut(),
                demande.getAvoir() == null ? null : demande.getAvoir().getReference(),
                demande.getAvoir() == null ? null : demande.getAvoir().getNumero());
    }

    /** Le lien de telechargement n apparait que si la note de credit existe. */
    public boolean aUnAvoir() {
        return referenceAvoir != null;
    }

    public boolean estEnAttente() {
        return statutDemande == StatutDemandeAnnulation.EN_ATTENTE;
    }
}
