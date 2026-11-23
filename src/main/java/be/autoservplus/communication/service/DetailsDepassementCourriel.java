package be.autoservplus.communication.service;

import java.util.List;

/**
 * Éléments d'un dépassement de devis (RM-15) nécessaires à la rédaction du courriel de
 * demande de validation.
 *
 * <p>Le courriel se construit autour de l'intervention et de son véhicule, et non d'un
 * rendez-vous : une intervention née d'une commande de prestations, ou entrée directement
 * au garage, n'en a pas, et son titulaire doit être prévenu de la même façon.</p>
 *
 * <p>Montants et libellés arrivent déjà formatés : le module {@code communication} ne
 * dépend ni de {@code intervention} ni du fuseau ou de la langue de l'appelant.</p>
 *
 * @param numeroIntervention numéro lisible de l'intervention concernée
 * @param vehicule           véhicule tel que le membre le reconnaît : marque, modèle, plaque
 * @param montantInitial     devis de référence, formaté en euros hors TVA
 * @param montantPropose     total hors TVA si le membre accepte les lignes en attente
 * @param montantAPayer      ce même total taxe comprise : le membre accepte un montant à
 *                           payer, il le lit TVA comprise comme sur l'écran de validation
 * @param lignesEnAttente    désignation et prix de chaque ligne soumise à l'accord
 * @param cheminValidation   chemin relatif de l'écran où le membre accepte ou refuse,
 *                           rendu absolu à la rédaction
 */
public record DetailsDepassementCourriel(String numeroIntervention,
                                         String vehicule,
                                         String montantInitial,
                                         String montantPropose,
                                         String montantAPayer,
                                         List<String> lignesEnAttente,
                                         String cheminValidation) {

    public DetailsDepassementCourriel {
        lignesEnAttente = List.copyOf(lignesEnAttente);
    }
}
