package be.autoservplus.pilotage.service.dto;



import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Instantane de pilotage du garage (BL-1), pour le mois en cours.
 *
 * <p>Assemblage en lecture seule : aucun indicateur n est stocke, tous sont recalcules
 * a chaque affichage. Un tableau de bord qui memoriserait ses chiffres devrait etre
 * invalide a chaque ecriture metier, et finirait par mentir.</p>
 *
 * @param minutesReservees minutes de rendez-vous non annules du mois
 * @param minutesCapacite  minutes ouvrables du mois, postes actifs compris ; zero si
 *                         aucune plage d ouverture n est definie
 */
public record TableauDeBord(
        String mois,
        MontantPeriode chiffreAffaire,
        MontantPeriode avoirs,
        MontantPeriode aEncaisser,
        List<RepartitionStatut> rendezVous,
        long minutesReservees,
        long minutesCapacite,
        int postesActifs,
        List<LigneClassement> topPrestations,
        List<LigneClassement> topPieces,
        List<PieceEnAlerte> piecesEnAlerte) {

    /**
     * Taux d occupation de l atelier, en pourcentage entier.
     *
     * <p>Rend {@code null} quand la capacite est inconnue — aucune plage d ouverture,
     * ou aucun poste actif. Afficher « 0 % » laisserait croire a un atelier vide alors
     * que la question n a pas de reponse, et un taux calcule sur une capacite nulle
     * n aurait aucun sens.</p>
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

    /** Chiffre d affaires net des avoirs, HTVA. */
    public BigDecimal chiffreAffaireNetHtva() {
        return chiffreAffaire.htva().subtract(avoirs.htva());
    }
}
