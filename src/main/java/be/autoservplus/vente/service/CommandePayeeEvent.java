package be.autoservplus.vente.service;

import java.util.UUID;

/**
 * Evenement applicatif publie au passage d une commande a PAYEE (F14).
 *
 * <p>Meme modele que {@code InterventionTermineeEvent} : la reference seule,
 * jamais l entite (le listener post-commit recharge dans sa propre transaction).
 * Publie UNE seule fois par commande — l idempotence du webhook garantit qu un
 * rejeu ne republie pas. C est le point d accroche de la generation de facture
 * (RM-22, bloc suivant) : aucun listener de facturation n existe encore.</p>
 */
public record CommandePayeeEvent(UUID referenceCommande) {
}
