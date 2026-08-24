package be.autoservplus.vente.service;

import be.autoservplus.config.MollieProprietes;
import be.autoservplus.vente.domain.StatutPaiement;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Passerelle Mollie : UNIQUE point de contact avec l API du prestataire
 * (strategie securite §11). Active des qu un identifiant est configure — partout
 * ailleurs, {@link PrestatairePaiementFictif} tient le role sans reseau.
 *
 * <p><b>La cle decide, plus le profil.</b> Le choix se faisait par
 * {@code @Profile("prod")}, ce qui liait deux questions independantes : deployer en
 * production sans identifiant levait une exception au clic sur « payer », et
 * demarrer en demonstration avec un identifiant valide l ignorait. Voir
 * {@link ConditionPrestataire}.</p>
 *
 * <p><b>Ecrite sur {@code RestClient}, sans SDK.</b> Trois appels REST sur des
 * documents JSON plats ne justifient pas une dependance : le SDK Mollie aurait
 * ajoute un cycle de vie a suivre, ses propres exceptions a retraduire, et une
 * surface bien plus large que les trois routes employees. {@code RestClient} est
 * deja la, apporte par {@code spring-boot-starter-web}.</p>
 *
 * <p><b>Le vocabulaire Mollie ne sort pas d ici.</b> Les records de dialogue sont
 * confines a cette classe, les statuts sont projetes vers {@link StatutPaiement}, et
 * aucune route d AutoServ+ n est composee ici — les URL de retour et de
 * notification arrivent toutes faites dans {@link DemandePaiement}. Changer de
 * prestataire ne toucherait que ce fichier.</p>
 */
@Service
@SiPrestataireConfigure
public class MollieGateway implements PrestatairePaiement {

    private static final Logger JOURNAL = LoggerFactory.getLogger(MollieGateway.class);

    /** Timeout strict des appels synchrones au prestataire (strategie securite §11). */
    public static final Duration DELAI_MAXIMUM = Duration.ofSeconds(5);

    /**
     * Hotes que le prestataire ne peut pas joindre depuis l exterieur. Une URL de
     * notification pointant l un d eux ferait rejeter la creation du paiement par
     * Mollie ; mieux vaut ne pas la transmettre du tout et laisser la reconciliation
     * au retour faire le travail.
     */
    private static final Set<String> HOTES_NON_JOIGNABLES =
            Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]");

    private final RestClient client;
    private final MollieProprietes proprietes;

    /**
     * Refuse de demarrer sur une configuration qui echouerait au premier paiement
     * (voir {@link MollieProprietes#verifierCoherence()}). L arret vaut mieux que
     * les deux alternatives : rompre devant un client au moment de payer, ou se
     * rabattre en silence sur la simulation alors qu un identifiant reel a ete
     * fourni — c est-a-dire faire croire a un encaissement qui n a pas lieu.
     */
    public MollieGateway(RestClient clientMollie, MollieProprietes proprietes) {
        proprietes.verifierCoherence();
        this.client = clientMollie;
        this.proprietes = proprietes;
        JOURNAL.info("Prestataire de paiement Mollie actif (mode {}{}).",
                proprietes.modeTest() ? "TEST" : "REEL",
                proprietes.estJetonOrganisation()
                        ? ", jeton d organisation, profil " + proprietes.profilId()
                        : ", cle API");
    }

    // --- creation ---------------------------------------------------------------------

    @Override
    public PaiementCree creerPaiement(DemandePaiement demande) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("amount", Map.of(
                "currency", demande.devise(),
                "value", montantMollie(demande.montantTvac())));
        // Le libelle atterrit sur l extrait bancaire du membre : le numero de commande
        // est ce qui lui permet de rapprocher une ligne de compte d un achat.
        corps.put("description", "Commande " + demande.numeroCommande());
        corps.put("redirectUrl", demande.urlRetour());
        ajouterNotification(corps, demande.urlNotification());
        ajouterContexteOrganisation(corps);

        PaiementMollie reponse = appeler(() -> client.post()
                .uri("/payments")
                // Cle d idempotence portee par la demande : une requete rejouee vers
                // Mollie ne cree pas un second paiement, donc ne debite pas deux fois.
                .header("Idempotency-Key", demande.cleIdempotence())
                .body(corps)
                .retrieve()
                .body(PaiementMollie.class), "creation du paiement");

        verifierReponse(reponse, demande);
        return new PaiementCree(reponse.id(), reponse.urlDePaiement());
    }

    /**
     * Mollie REFUSE une URL de notification qu il ne peut pas joindre — donc toute
     * adresse locale. La transmettre ferait echouer la creation du paiement en
     * developpement, la ou tout le reste fonctionne.
     *
     * <p>L omettre n est pas une perte : le prestataire n envoie alors aucune
     * notification, et c est la reconciliation au retour du membre qui constate le
     * statut. Le meme chemin idempotent, declenche par un autre evenement.</p>
     */
    private void ajouterNotification(Map<String, Object> corps, String urlNotification) {
        if (urlNotification == null || urlNotification.isBlank()) {
            return;
        }
        if (joignableDepuisInternet(urlNotification)) {
            corps.put("webhookUrl", urlNotification);
        } else {
            JOURNAL.info("URL de notification {} non joignable depuis l exterieur : "
                    + "elle n est pas transmise au prestataire, le statut sera constate "
                    + "au retour du membre.", urlNotification);
        }
    }

    private static boolean joignableDepuisInternet(String url) {
        try {
            String hote = URI.create(url).getHost();
            if (hote == null) {
                return false;
            }
            String normalise = hote.toLowerCase(Locale.ROOT);
            // Un hote sans point ne porte aucun domaine public ; les TLD reserves
            // (RFC 2606 / RFC 6761) ne se resolvent nulle part hors du poste.
            return !HOTES_NON_JOIGNABLES.contains(normalise)
                    && normalise.contains(".")
                    && !normalise.endsWith(".local")
                    && !normalise.endsWith(".test")
                    && !normalise.endsWith(".localhost")
                    && !normalise.endsWith(".invalid");
        } catch (IllegalArgumentException urlMalformee) {
            return false;
        }
    }

    /**
     * Un jeton d acces organisation n est rattache a aucun profil de site : Mollie
     * ne peut deduire ni ou imputer le paiement, ni s il est de test. Une cle API
     * porte les deux, et Mollie refuse alors ces memes champs — d ou la condition
     * plutot qu un envoi systematique.
     */
    private void ajouterContexteOrganisation(Map<String, Object> corps) {
        if (proprietes.estJetonOrganisation()) {
            corps.put("profileId", proprietes.profilId());
            corps.put("testmode", proprietes.modeTest());
        }
    }

    /**
     * Un paiement cree pour un autre montant ou une autre devise que ceux demandes
     * n est pas le paiement de cette commande. Le constater ici evite de poser sur la
     * commande une reference qui encaisserait autre chose que son du.
     */
    private void verifierReponse(PaiementMollie reponse, DemandePaiement demande) {
        if (reponse == null || reponse.id() == null || reponse.urlDePaiement() == null) {
            throw new PrestataireIndisponibleException(
                    "Reponse de creation de paiement incomplete (identifiant ou URL absent).");
        }
        String attendu = montantMollie(demande.montantTvac());
        if (reponse.amount() == null
                || !attendu.equals(reponse.amount().value())
                || !demande.devise().equals(reponse.amount().currency())) {
            throw new PrestataireIndisponibleException(
                    "Montant confirme par le prestataire different du montant demande.");
        }
    }

    // --- relecture --------------------------------------------------------------------

    @Override
    public StatutPaiement lireStatut(String referencePrestataire) {
        PaiementMollie reponse = appeler(() -> client.get()
                        .uri(constructeur -> constructeur.path("/payments/{id}")
                                // En GET, le mode test se transmet en parametre d URL et
                                // non dans un corps qui n existe pas.
                                .queryParamIfPresent("testmode", modeTestEventuel())
                                .build(referencePrestataire))
                        .retrieve()
                        .body(PaiementMollie.class),
                "relecture du statut du paiement");

        if (reponse == null || reponse.status() == null) {
            throw new PrestataireIndisponibleException(
                    "Reponse de relecture sans statut pour " + referencePrestataire + ".");
        }
        return projeter(reponse.status());
    }

    private java.util.Optional<Boolean> modeTestEventuel() {
        return proprietes.estJetonOrganisation()
                ? java.util.Optional.of(proprietes.modeTest())
                : java.util.Optional.empty();
    }

    /**
     * Projection des statuts Mollie vers le vocabulaire du domaine.
     *
     * <p>{@code authorized} rejoint EN_COURS et non REUSSI : l autorisation reserve
     * les fonds sans les capturer. Les traiter comme un encaissement decrementerait
     * le stock et emettrait une facture pour un montant qui peut encore ne jamais
     * arriver.</p>
     *
     * <p>Un statut inconnu leve plutot que de tomber sur une valeur par defaut : se
     * rabattre sur ECHOUE annulerait peut-etre un paiement reussi, et sur REUSSI
     * livrerait peut-etre sans encaissement. L incertitude doit remonter, pas se
     * resoudre au hasard.</p>
     */
    private StatutPaiement projeter(String statutMollie) {
        return switch (statutMollie) {
            case "open" -> StatutPaiement.INITIE;
            case "pending", "authorized" -> StatutPaiement.EN_COURS;
            case "paid" -> StatutPaiement.REUSSI;
            case "failed", "canceled" -> StatutPaiement.ECHOUE;
            case "expired" -> StatutPaiement.EXPIRE;
            default -> throw new PrestataireIndisponibleException(
                    "Statut de paiement inconnu du prestataire : " + statutMollie);
        };
    }

    // --- remboursement ----------------------------------------------------------------

    @Override
    public RemboursementCree rembourser(DemandeRemboursement demande) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("amount", Map.of(
                "currency", demande.devise(),
                "value", montantMollie(demande.montantTvac())));
        corps.put("description", "Remboursement " + demande.referencePrestataire());
        if (proprietes.estJetonOrganisation()) {
            // Pas de profileId ici : le Refund se rattache au paiement d origine, qui
            // porte deja son profil. Seul le mode reste a preciser.
            corps.put("testmode", proprietes.modeTest());
        }

        RemboursementMollie reponse = appeler(() -> client.post()
                        .uri("/payments/{id}/refunds", demande.referencePrestataire())
                        // Cle DERIVEE du paiement, donc stable au rejeu : c est elle qui
                        // empeche de rembourser deux fois.
                        .header("Idempotency-Key", demande.cleIdempotence())
                        .body(corps)
                        .retrieve()
                        .body(RemboursementMollie.class),
                "remboursement du paiement");

        if (reponse == null || reponse.id() == null) {
            throw new PrestataireIndisponibleException(
                    "Reponse de remboursement sans identifiant.");
        }
        String attendu = montantMollie(demande.montantTvac());
        if (reponse.amount() == null || !attendu.equals(reponse.amount().value())) {
            throw new PrestataireIndisponibleException(
                    "Montant rembourse different du montant demande.");
        }
        // Un Refund Mollie nait « pending » et ne devient « refunded » qu apres
        // execution bancaire. Le contrat de PrestatairePaiement suppose une reponse
        // synchrone : l acceptation du Refund vaut ici engagement. Le passage a
        // l asynchrone (webhook de remboursement, statut intermediaire sur paiement)
        // reste la dette V2 inscrite au registre, il ne se resout pas dans ce fichier.
        return new RemboursementCree(reponse.id());
    }

    // --- plomberie --------------------------------------------------------------------

    /**
     * Deux decimales et un point decimal, quelle que soit la locale du serveur :
     * Mollie attend une chaine, et un montant formate a la belge (« 80,21 ») serait
     * rejete ou, pire, mal interprete.
     */
    private static String montantMollie(BigDecimal montant) {
        return montant.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Enveloppe tout appel sortant : aucune exception du client HTTP ne franchit
     * cette classe sous sa forme d origine.
     *
     * <p>Le message conserve l operation mais jamais le corps de la reponse, qui peut
     * porter des identifiants de requete et des details d infrastructure. La cause est
     * attachee pour le journal ; ce qui s affiche au membre vient de l i18n.</p>
     */
    private <T> T appeler(java.util.function.Supplier<T> appel, String operation) {
        try {
            return appel.get();
        } catch (RestClientException echec) {
            throw new PrestataireIndisponibleException(
                    "Echec de l appel au prestataire de paiement (" + operation + ").", echec);
        }
    }

    // --- dialogue Mollie, confine a cette classe ---------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MontantMollie(String currency, String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LienMollie(String href) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LiensMollie(LienMollie checkout) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaiementMollie(String id, String status, MontantMollie amount,
                          @JsonProperty("_links") LiensMollie links) {

        /** URL de la page de paiement, absente des paiements deja aboutis. */
        String urlDePaiement() {
            return links == null || links.checkout() == null ? null : links.checkout().href();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RemboursementMollie(String id, String status, MontantMollie amount) {
    }
}
