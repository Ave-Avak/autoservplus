package be.autoservplus.vente.service;

import be.autoservplus.vente.domain.StatutPaiement;

/**
 * Ce que le prestataire rapporte d'un paiement quand on le relit : son statut
 * authentique, le moyen effectivement employé s'il est déjà connu, et l'adresse de
 * sa page de paiement tant qu'elle est ouverte.
 *
 * <p><b>Un record plutôt que le seul statut</b>, parce que ces informations
 * arrivent dans la même réponse. Les séparer imposerait un second appel réseau pour
 * une donnée déjà reçue, et un appel de plus à l'intérieur de la transaction qui
 * relit le statut. Cette transaction tient déjà une connexion à la base pendant un
 * aller-retour vers le prestataire, borné seulement par le délai de
 * {@code MollieGateway.DELAI_MAXIMUM}. Le correctif, sortir l'appel de la transaction,
 * reste pour la V2, et rien ne doit l'aggraver d'ici là.</p>
 *
 * @param statut      statut projeté vers le vocabulaire du domaine
 * @param methode     moyen employé tel que le prestataire le nomme (bancontact, carte,
 *                    virement...), ou {@code null} tant qu'il ne le connaît pas : le
 *                    client choisit sur la page du prestataire, donc après la création
 *                    du paiement. Un prestataire bouchonné n'en rapporte aucun, et
 *                    l'écran de détail dit alors que le moyen n'a pas été communiqué
 *                    plutôt que d'en inventer un.
 * @param urlPaiement page de paiement encore ouverte, ou {@code null} : le prestataire
 *                    ne la rend que pour un paiement qui attend le client. C'est elle
 *                    qui permet de reprendre une tentative au lieu d'en créer une
 *                    seconde.
 * @param annulable   le prestataire accepte encore d'annuler ce paiement. Le job RM-21
 *                    n'annule que sur cette foi : la plupart des moyens ne le
 *                    permettent pas une fois le client parti chez sa banque, et le
 *                    prestataire ne rapporte rien pour un paiement clos.
 */
public record EtatPaiement(StatutPaiement statut, String methode, String urlPaiement,
                           boolean annulable) {

    /** État d'un paiement que le prestataire ne dit pas annulable. */
    public EtatPaiement(StatutPaiement statut, String methode, String urlPaiement) {
        this(statut, methode, urlPaiement, false);
    }

    /** État sans page de paiement ouverte. */
    public EtatPaiement(StatutPaiement statut, String methode) {
        this(statut, methode, null, false);
    }

    /** État sans moyen connu ni page ouverte. */
    public static EtatPaiement de(StatutPaiement statut) {
        return new EtatPaiement(statut, null, null, false);
    }
}
