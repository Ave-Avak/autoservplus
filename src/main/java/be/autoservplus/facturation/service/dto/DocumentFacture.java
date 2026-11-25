package be.autoservplus.facturation.service.dto;

import be.autoservplus.facturation.service.VentilationTva;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Tout ce qu'il faut imprimer sur une facture, et rien d'autre.
 *
 * <p>Le générateur PDF ne voit aucune entité : il reçoit ce document déjà constitué
 * et n'a donc ni contexte de persistance à tenir ouvert, ni association paresseuse à
 * déclencher. Il devient testable sans base ni Spring.</p>
 *
 * <p>{@code locale} est celle du <b>membre</b> (colonne {@code utilisateur.langue}) et
 * non celle du navigateur : une facture est un document contractuel, elle est émise
 * dans la langue du client, pas dans celle de la session qui la télécharge.</p>
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
        Locale locale,
        String numeroIntervention,
        LocalDate echeance,
        String factureCompletee,
        Instant dateFactureCompletee) {

    /** Facture d'une commande payée : ni intervention, ni échéance, ni facture complétée. */
    public DocumentFacture(String numero, Instant dateEmission, String numeroCommande, Instant datePaiement,
                           ClientFacture client, List<LigneFacture> lignes, VentilationTva ventilation,
                           BigDecimal totalHtva, BigDecimal totalTva, BigDecimal totalTvac, Locale locale) {
        this(numero, dateEmission, numeroCommande, datePaiement, client, lignes, ventilation, totalHtva,
                totalTva, totalTvac, locale, null, null, null, null);
    }

    /** Facture d'atelier : à régler à l'atelier avant son échéance, rien n'est encore payé. */
    public boolean estDAtelier() {
        return numeroIntervention != null;
    }

    /**
     * Coordonnées du client telles qu'elles étaient à l'émission.
     *
     * <p>Le nom arrive <b>composé</b> plutôt qu'en prénom et nom séparés : le document
     * ne les affiche jamais séparément, et un compte anonymisé n'a pas de prénom ni de
     * nom mais un marqueur unique, traduit dans la langue du document (F23). Deux
     * champs auraient forcé à couper ce marqueur en deux.</p>
     */
    public record ClientFacture(
            String nomComplet,
            String rue,
            String numeroRue,
            String codePostal,
            String localite,
            String pays) {

        /**
         * Adresse sur une ligne, ou {@code null} si le membre n'en a pas renseigné :
         * les colonnes d'adresse sont nullables en base, et une facture doit sortir
         * même pour un client qui n'a jamais complété son profil.
         */
        public String adresseLisible() {
            if (rue == null || codePostal == null || localite == null) {
                return null;
            }
            return "%s %s, %s %s".formatted(rue, numeroRue == null ? "" : numeroRue,
                    codePostal, localite).replace("  ", " ");
        }
    }

    /**
     * Une ligne facturée, aux valeurs figées à la commande : le prix unitaire hors TVA et le
     * taux qu'exige la facture, et le total taxe comprise que le client a payé. La base
     * imposable n'existe que par taux, à la ventilation.
     */
    /**
     * @param quantite rédigée dans la langue du document : « 2 » pour une pièce ou un
     *                 forfait, « 1,5 h » pour des heures de main-d'œuvre
     */
    public record LigneFacture(
            String libelle,
            String quantite,
            BigDecimal prixUnitaireHtva,
            BigDecimal tauxTva,
            BigDecimal totalTvac) {

        /** Ligne à quantité entière, celle d'une commande. */
        public LigneFacture(String libelle, int quantite, BigDecimal prixUnitaireHtva, BigDecimal tauxTva,
                            BigDecimal totalTvac) {
            this(libelle, String.valueOf(quantite), prixUnitaireHtva, tauxTva, totalTvac);
        }
    }
}
