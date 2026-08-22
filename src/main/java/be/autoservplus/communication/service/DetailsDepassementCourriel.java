package be.autoservplus.communication.service;

import java.util.List;

/**
 * Elements d un depassement de devis (RM-15) necessaires a la redaction du courriel
 * de demande de validation.
 *
 * <p>Meme principe que {@link DetailsRdvCourriel} : montants et libelles arrivent
 * deja formates, le module {@code communication} ne depend ni de {@code intervention}
 * ni du fuseau ou de la locale de l appelant.</p>
 *
 * @param numeroIntervention numero lisible de l intervention concernee
 * @param montantInitial     devis accepte a la reservation, formate en euros HTVA
 * @param montantPropose     total HTVA si le membre accepte les lignes en attente
 * @param lignesEnAttente    designation et prix de chaque ligne soumise a l accord
 * @param lienValidation     URL de l ecran ou le membre accepte ou refuse
 */
public record DetailsDepassementCourriel(String numeroIntervention,
                                         String montantInitial,
                                         String montantPropose,
                                         List<String> lignesEnAttente,
                                         String lienValidation) {

    public DetailsDepassementCourriel {
        lignesEnAttente = List.copyOf(lignesEnAttente);
    }
}
