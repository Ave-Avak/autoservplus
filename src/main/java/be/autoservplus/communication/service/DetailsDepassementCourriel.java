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
 * <p>Chaque montant est annoncé taxe comprise, le prix qu'un consommateur paie (RM-30), avec
 * son équivalent hors TVA : le seuil de RM-15 se mesure hors TVA, mais le membre accepte un
 * prix à payer, comme sur l'écran de validation.</p>
 *
 * @param montantInitial     devis initial taxe comprise, formaté en euros
 * @param montantInitialHtva ce même devis hors TVA
 * @param montantAPayer      total taxe comprise si le membre accepte les lignes en attente
 * @param montantPropose     ce même total hors TVA
 * @param lignesEnAttente    désignation et prix taxe comprise de chaque ligne soumise à l'accord
 * @param cheminValidation   chemin relatif de l'écran où le membre accepte ou refuse,
 *                           rendu absolu à la rédaction
 */
public record DetailsDepassementCourriel(String numeroIntervention,
                                         String vehicule,
                                         String montantInitial,
                                         String montantInitialHtva,
                                         String montantAPayer,
                                         String montantPropose,
                                         List<String> lignesEnAttente,
                                         String cheminValidation) {

    public DetailsDepassementCourriel {
        lignesEnAttente = List.copyOf(lignesEnAttente);
    }
}
