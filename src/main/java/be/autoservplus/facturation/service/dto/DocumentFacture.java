package be.autoservplus.facturation.service.dto;

import be.autoservplus.facturation.service.VentilationTva;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Tout ce qu il faut imprimer sur une facture, et rien d autre.
 *
 * <p>Le generateur PDF ne voit aucune entite : il recoit ce document deja constitue
 * et n a donc ni contexte de persistance a tenir ouvert, ni association paresseuse a
 * declencher. Il devient testable sans base ni Spring.</p>
 *
 * <p>{@code locale} est celle du <b>membre</b> (colonne {@code utilisateur.langue}) et
 * non celle du navigateur : une facture est un document contractuel, elle est emise
 * dans la langue du client, pas dans celle de la session qui la telecharge.</p>
 */
public record DocumentFacture(
        String numero,
        Instant dateEmission,
        String numeroCommande,
        Instant datePaiement,
        ClientFacture client,
        List<LigneFacture> lignes,
        VentilationTva ventilation,
        BigDecimal totalHtva,
        BigDecimal totalTva,
        BigDecimal totalTvac,
        Locale locale) {

    /** Coordonnees du client telles qu elles etaient a l emission. */
    public record ClientFacture(
            String prenom,
            String nom,
            String rue,
            String numeroRue,
            String codePostal,
            String localite,
            String pays,
            String courriel) {

        public String nomComplet() {
            return "%s %s".formatted(prenom, nom);
        }

        /**
         * Adresse sur une ligne, ou {@code null} si le membre n en a pas renseigne :
         * les colonnes d adresse sont nullables en base, et une facture doit sortir
         * meme pour un client qui n a jamais complete son profil.
         */
        public String adresseLisible() {
            if (rue == null || codePostal == null || localite == null) {
                return null;
            }
            return "%s %s, %s %s".formatted(rue, numeroRue == null ? "" : numeroRue,
                    codePostal, localite).replace("  ", " ");
        }
    }

    /** Une ligne facturee, aux valeurs figees a la commande. */
    public record LigneFacture(
            String libelle,
            int quantite,
            BigDecimal prixUnitaireHtva,
            BigDecimal tauxTva,
            BigDecimal totalHtva) {
    }
}
