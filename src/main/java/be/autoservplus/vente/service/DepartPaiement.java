package be.autoservplus.vente.service;

import java.util.Objects;

/**
 * Ce que produit le geste « payer » d'un membre (F14) : une page de paiement où
 * l'envoyer, ou la raison pour laquelle il n'y en a pas.
 *
 * <p>Seule {@link Issue#REDIRECTION} porte une adresse. Les trois autres issues
 * ramènent le membre à sa commande avec un message : aucune ne crée de paiement.</p>
 *
 * @param issue          ce que le contrôleur doit faire du membre
 * @param urlRedirection page de paiement, tentative reprise ou nouvelle, ou
 *                       {@code null} hors {@link Issue#REDIRECTION}
 */
public record DepartPaiement(Issue issue, String urlRedirection) {

    public enum Issue {
        /** Page de paiement d'une tentative reprise ou nouvelle. */
        REDIRECTION,
        /** La relecture de la tentative ouverte a constaté l'encaissement. */
        DEJA_PAYEE,
        /**
         * Une tentative est en cours de traitement chez le prestataire, sans page de
         * paiement (virement, autorisation de carte). Une seconde tentative pourrait
         * encaisser deux fois.
         */
        EN_COURS,
        /** Un autre geste du membre vient d'ouvrir une tentative pour la même commande. */
        TENTATIVE_CONCURRENTE
    }

    public DepartPaiement {
        Objects.requireNonNull(issue, "issue");
        if ((issue == Issue.REDIRECTION) != (urlRedirection != null)) {
            throw new IllegalArgumentException(
                    "Une adresse accompagne la redirection, et elle seule.");
        }
    }

    public static DepartPaiement vers(String urlRedirection) {
        return new DepartPaiement(Issue.REDIRECTION,
                Objects.requireNonNull(urlRedirection, "urlRedirection"));
    }

    public static DepartPaiement sans(Issue issue) {
        return new DepartPaiement(issue, null);
    }
}
