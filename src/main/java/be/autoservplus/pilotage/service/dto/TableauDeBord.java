package be.autoservplus.pilotage.service.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Indicateurs de pilotage du garage sur une période (A25).
 *
 * <p>Assemblage en lecture seule : aucun indicateur n'est stocké, tous sont recalculés
 * à chaque affichage. Un tableau de bord qui mémoriserait ses chiffres devrait être
 * invalidé à chaque écriture métier, et finirait par mentir.</p>
 *
 * <p>Deux indicateurs ne suivent pas la période, parce qu'ils décrivent l'état du
 * moment : les commandes à encaisser et le stock à surveiller. La série des trente
 * derniers jours est fixe elle aussi, pour que la courbe garde la même échelle de temps
 * d'une visite à l'autre.</p>
 *
 * @param commandesPayees  commandes payées pendant la période, assiette du panier moyen
 * @param minutesReservees minutes de rendez-vous non annulés de la période
 * @param minutesCapacite  minutes ouvrables de la période, postes actifs compris. Zéro
 *                         si aucune plage d'ouverture n'est définie
 * @param parCategorie     chiffre d'affaires facturé par catégorie, du plus fort au plus faible
 * @param trenteJours      chiffre d'affaires facturé par jour, trente jours jusqu'à aujourd'hui
 */
public record TableauDeBord(
        PeriodeTableau periode,
        MontantPeriode chiffreAffaire,
        MontantPeriode avoirs,
        MontantPeriode aEncaisser,
        List<RepartitionStatut> rendezVous,
        Annulations annulations,
        long nouveauxClients,
        MontantPeriode commandesPayees,
        long minutesReservees,
        long minutesCapacite,
        int postesActifs,
        List<LigneClassement> topPrestations,
        List<LigneClassement> topPieces,
        List<MontantCategorie> parCategorie,
        List<JourChiffre> trenteJours,
        List<PieceEnAlerte> piecesEnAlerte) {

    /**
     * Taux d'occupation de l'atelier, en pourcentage entier.
     *
     * <p>Rend {@code null} quand la capacité est inconnue : aucune plage d'ouverture,
     * ou aucun poste actif. Afficher « 0 % » laisserait croire à un atelier vide alors
     * que la question n'a pas de réponse, et un taux calculé sur une capacité nulle
     * n'aurait aucun sens.</p>
     */
    public Integer tauxOccupation() {
        if (minutesCapacite <= 0) {
            return null;
        }
        return BigDecimal.valueOf(minutesReservees)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(minutesCapacite), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    public boolean capaciteConnue() {
        return minutesCapacite > 0;
    }

    /** Chiffre d'affaires net des avoirs, HTVA. */
    public BigDecimal chiffreAffaireNetHtva() {
        return chiffreAffaire.htva().subtract(avoirs.htva());
    }

    /**
     * Panier moyen hors TVA : montant des commandes payées pendant la période, divisé par
     * leur nombre. Les avoirs n'en sont pas déduits : une commande remboursée plus tard
     * a bien été passée à ce montant, et le panier moyen décrit ce que les clients
     * commandent. Nul sans commande payée.
     */
    public BigDecimal panierMoyenHtva() {
        if (commandesPayees.estVide()) {
            return null;
        }
        return commandesPayees.htva()
                .divide(BigDecimal.valueOf(commandesPayees.nombre()), 2, RoundingMode.HALF_UP);
    }
}
