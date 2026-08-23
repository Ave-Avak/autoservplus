package be.autoservplus.reservation.service;

import java.util.UUID;

/**
 * Evenement applicatif publie a chaque transition de statut d un rendez-vous decidee
 * par le garage (F16).
 *
 * <p>Meme modele que {@code CommandePayeeEvent} : la reference seule, jamais l entite.
 * Le listener s execute apres commit, hors de la session Hibernate d origine, et lit
 * alors le statut <b>reellement committe</b> plutot qu une valeur figee au moment de la
 * publication.</p>
 *
 * <p><b>Un seul evenement pour les cinq transitions</b> (confirme, refuse, annule,
 * honore, absent), et non cinq evenements : elles declenchent toutes le meme geste —
 * prevenir le membre que son dossier a bouge. Le listener lit le statut d arrivee pour
 * choisir le libelle. Scinder l evenement obligerait a le republier a chaque nouvelle
 * transition ajoutee a la machine a etats.</p>
 *
 * <p>Publie depuis le point d ecriture unique {@code AdminRdvService.ecrire} : aucune
 * transition ne peut lui echapper, y compris celles ajoutees plus tard.</p>
 */
public record RdvStatutModifieEvent(UUID referenceRdv) {
}
