package be.autoservplus.vente.service;

import java.util.UUID;

/**
 * Événement applicatif publié au passage d'une commande à PAYEE (F14).
 *
 * <p>Même modèle que {@code InterventionTermineeEvent} : la référence seule,
 * jamais l'entité (le listener post-commit recharge dans sa propre transaction).
 * Publié UNE seule fois par commande : l'idempotence du webhook garantit qu'un
 * rejeu ne republie pas. C'est le point d'accroche de la génération de facture
 * (RM-22, bloc suivant) : aucun listener de facturation n'existe encore.</p>
 */
public record CommandePayeeEvent(UUID referenceCommande) {
}
