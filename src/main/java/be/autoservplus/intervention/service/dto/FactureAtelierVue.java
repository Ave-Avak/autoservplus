package be.autoservplus.intervention.service.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Facture d'atelier d'une intervention telle que l'écran la montre (A15, RM-17) : de quoi
 * en afficher le statut et en télécharger le PDF. Le PDF est celui de l'émission, figé : le
 * statut se lit ici, jamais sur le document (Q-H-49).
 *
 * @param cleStatut clé i18n du statut : à régler, ou rien à régler pour une facture à 0 €
 * @param referenceAvoir note de crédit qui annule la facture, nulle sinon (Q-H-51)
 * @param regleeLe       jour de l'encaissement non annulé, nul sinon (Q-H-43)
 * @param moyen          moyen de cet encaissement, sous sa forme stable
 * @param aujourdHui     jour courant au fuseau du garage, proposé au formulaire d'encaissement
 */
public record FactureAtelierVue(UUID reference, String numero, String montantTvac, LocalDate echeance,
                                String cleStatut, UUID referenceAvoir, String numeroAvoir,
                                LocalDate regleeLe, String moyen, LocalDate aujourdHui) {

    public boolean estAnnulee() {
        return referenceAvoir != null;
    }

    public boolean estReglee() {
        return regleeLe != null;
    }

    /** Une facture à 0 € n'a rien à régler (Q-H-44), une facture annulée ne s'encaisse plus (Q-H-46). */
    public boolean peutEtreEncaissee() {
        return !estAnnulee() && !estReglee() && "facture.statut.a-regler".equals(cleStatut);
    }

    /** Réglée puis annulée : le remboursement au client se fait hors de l'application (Q-H-46). */
    public boolean remboursementHorsApplication() {
        return estAnnulee() && estReglee();
    }
}
