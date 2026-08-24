package be.autoservplus.vente.service;

import be.autoservplus.vente.domain.StatutPaiement;

/**
 * Ce que le prestataire rapporte d un paiement quand on le relit : son statut
 * authentique, et le moyen effectivement employe s il est deja connu.
 *
 * <p><b>Un record plutot que le seul statut</b>, parce que les deux informations
 * arrivent dans la meme reponse. Les separer imposerait un second appel reseau pour
 * une donnee deja recue — et un appel de plus a l interieur de la transaction qui
 * relit le statut, c est-a-dire exactement ce que le registre de dette signale
 * comme a surveiller.</p>
 *
 * @param statut  statut projete vers le vocabulaire du domaine
 * @param methode moyen employe tel que le prestataire le nomme (bancontact, carte,
 *                virement…), ou {@code null} tant qu il ne le connait pas : le
 *                client choisit sur la page du prestataire, donc apres la creation
 *                du paiement. Un prestataire bouchonne n en rapporte aucun, et
 *                l ecran de detail dit alors que le moyen n a pas ete communique
 *                plutot que d en inventer un.
 */
public record EtatPaiement(StatutPaiement statut, String methode) {

    /** Etat sans moyen connu, cas du prestataire bouchonne comme d un paiement naissant. */
    public static EtatPaiement de(StatutPaiement statut) {
        return new EtatPaiement(statut, null);
    }
}
