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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Passerelle Mollie : UNIQUE point de contact avec l'API du prestataire
 * (stratégie sécurité §11). Active dès qu'un identifiant est configuré : partout
 * ailleurs, {@link PrestatairePaiementFictif} tient le rôle sans réseau.
 *
 * <p><b>La clé décide, plus le profil.</b> Le choix se faisait par
 * {@code @Profile("prod")}, ce qui liait deux questions indépendantes : déployer en
 * production sans identifiant levait une exception au clic sur « payer », et
 * démarrer en démonstration avec un identifiant valide l'ignorait. Voir
 * {@link ConditionPrestataire}.</p>
 *
 * <p><b>Écrite sur {@code RestClient}, sans SDK.</b> Quatre appels REST sur des
 * documents JSON plats ne justifient pas une dépendance : le SDK Mollie aurait
 * ajouté un cycle de vie à suivre, ses propres exceptions à retraduire, et une
 * surface bien plus large que les quatre routes employées. {@code RestClient} est
 * déjà là, apporté par {@code spring-boot-starter-web}.</p>
 *
 * <p><b>Le vocabulaire Mollie ne sort pas d'ici.</b> Les records de dialogue sont
 * confinés à cette classe, les statuts sont projetés vers {@link StatutPaiement}, et
 * aucune route d'AutoServ+ n'est composée ici : les URL de retour et de
 * notification arrivent toutes faites dans {@link DemandePaiement}. Changer de
 * prestataire ne toucherait que ce fichier.</p>
 */
@Service
@SiPrestataireConfigure
public class MollieGateway implements PrestatairePaiement {

    private static final Logger JOURNAL = LoggerFactory.getLogger(MollieGateway.class);

    /** Timeout strict des appels synchrones au prestataire (stratégie sécurité §11). */
    public static final Duration DELAI_MAXIMUM = Duration.ofSeconds(5);

    /**
     * Hôtes que le prestataire ne peut pas joindre depuis l'extérieur. Une URL de
     * notification pointant l'un d'eux ferait rejeter la création du paiement par
     * Mollie. Mieux vaut ne pas la transmettre du tout et laisser la réconciliation
     * au retour faire le travail.
     */
    private static final Set<String> HOTES_NON_JOIGNABLES =
            Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]");

    /**
     * Moyens proposés sur la page de paiement, quel que soit le réglage du profil
     * Mollie. Le job RM-21 attend le statut définitif d'un paiement qu'il ne peut pas
     * annuler, et son plafond d'attente vient de l'expiration de ces deux moyens
     * (carte 15 minutes, Bancontact une heure, documentation Mollie, « Handling payment
     * status »). Un virement expirerait après douze jours : ajouter un moyen ici
     * suppose de revoir {@code ExpirationCommandesJob.ATTENTE_MAXIMALE}.
     */
    static final List<String> MOYENS_PROPOSES = List.of("bancontact", "creditcard");

    private final RestClient client;
    private final MollieProprietes proprietes;

    /**
     * Refuse de démarrer sur une configuration qui échouerait au premier paiement
     * (voir {@link MollieProprietes#verifierCoherence()}). L'arrêt vaut mieux que
     * les deux alternatives : rompre devant un client au moment de payer, ou se
     * rabattre en silence sur la simulation alors qu'un identifiant réel a été
     * fourni, c'est-à-dire faire croire à un encaissement qui n'a pas lieu.
     */
    public MollieGateway(RestClient clientMollie, MollieProprietes proprietes) {
        proprietes.verifierCoherence();
        this.client = clientMollie;
        this.proprietes = proprietes;
        JOURNAL.info("Prestataire de paiement Mollie actif (mode {}{}).",
                proprietes.modeTest() ? "TEST" : "REEL",
                proprietes.exigeContexteOrganisation()
                        ? ", jeton d acces, profil " + proprietes.profilId()
                        : ", cle API de site");
    }

    // --- création ---------------------------------------------------------------------

    @Override
    public PaiementCree creerPaiement(DemandePaiement demande) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("amount", Map.of(
                "currency", demande.devise(),
                "value", montantMollie(demande.montantTvac())));
        // Le libellé atterrit sur l'extrait bancaire du membre : le numéro de commande
        // est ce qui lui permet de rapprocher une ligne de compte d'un achat.
        corps.put("description", "Commande " + demande.numeroCommande());
        corps.put("redirectUrl", demande.urlRetour());
        corps.put("method", MOYENS_PROPOSES);
        ajouterNotification(corps, demande.urlNotification());
        ajouterContexteOrganisation(corps);

        PaiementMollie reponse = appeler(() -> client.post()
                .uri("/payments")
                // Clé d'idempotence portée par la demande : une requête rejouée vers
                // Mollie ne crée pas un second paiement, donc ne débite pas deux fois.
                .header("Idempotency-Key", demande.cleIdempotence())
                .body(corps)
                .retrieve()
                .body(PaiementMollie.class), "creation du paiement");

        verifierReponse(reponse, demande);
        return new PaiementCree(reponse.id(), reponse.urlDePaiement());
    }

    /**
     * Mollie REFUSE une URL de notification qu'il ne peut pas joindre, donc toute
     * adresse locale. La transmettre ferait échouer la création du paiement en
     * développement, là où tout le reste fonctionne.
     *
     * <p>L'omettre n'est pas une perte : le prestataire n'envoie alors aucune
     * notification, et c'est la réconciliation au retour du membre qui constate le
     * statut. Le même chemin idempotent, déclenché par un autre événement.</p>
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
            // Un hôte sans point ne porte aucun domaine public. Les TLD réservés
            // (RFC 2606 / RFC 6761) ne se résolvent nulle part hors du poste.
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
     * Un jeton d'accès n'est rattaché à aucun profil de site : Mollie ne peut déduire
     * ni où imputer le paiement, ni s'il est de test. Une clé API porte les deux, et
     * Mollie refuse alors ces mêmes champs : d'où la condition plutôt qu'un envoi
     * systématique.
     */
    private void ajouterContexteOrganisation(Map<String, Object> corps) {
        if (proprietes.exigeContexteOrganisation()) {
            corps.put("profileId", proprietes.profilId());
            corps.put("testmode", proprietes.modeTest());
        }
    }

    /**
     * Un paiement créé pour un autre montant ou une autre devise que ceux demandés
     * n'est pas le paiement de cette commande. Le constater ici évite de poser sur la
     * commande une référence qui encaisserait autre chose que son dû.
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
    public EtatPaiement lireEtat(String referencePrestataire) {
        PaiementMollie reponse = appeler(() -> client.get()
                        .uri(constructeur -> constructeur.path("/payments/{id}")
                                // En GET, le mode test se transmet en paramètre d'URL et
                                // non dans un corps qui n'existe pas.
                                .queryParamIfPresent("testmode", modeTestEventuel())
                                .build(referencePrestataire))
                        .retrieve()
                        .body(PaiementMollie.class),
                "relecture du statut du paiement");

        if (reponse == null || reponse.status() == null) {
            throw new PrestataireIndisponibleException(
                    "Reponse de relecture sans statut pour " + referencePrestataire + ".");
        }
        // Mollie ne rend la page de paiement que pour un paiement « open » : c'est elle
        // que la reprise d'une tentative ouverte renvoie au membre.
        return etat(reponse);
    }

    private EtatPaiement etat(PaiementMollie reponse) {
        // isCancelable est omis d'un paiement clos : absent vaut non.
        return new EtatPaiement(projeter(reponse.status()), reponse.method(),
                reponse.urlDePaiement(), Boolean.TRUE.equals(reponse.annulable()));
    }

    // --- annulation -------------------------------------------------------------------

    /**
     * {@code DELETE /payments/{id}}. Mollie répond par le paiement annulé, ou par une
     * erreur quand le paiement ne peut plus l'être : elle remonte comme toute autre,
     * et l'appelant relit au passage suivant.
     */
    @Override
    public EtatPaiement annuler(String referencePrestataire) {
        PaiementMollie reponse = appeler(() -> {
            var requete = client.method(org.springframework.http.HttpMethod.DELETE)
                    .uri("/payments/{id}", referencePrestataire);
            if (proprietes.exigeContexteOrganisation()) {
                // Pour cet appel, Mollie lit le mode test dans le corps, pas dans l'URL.
                requete.body(Map.of("testmode", proprietes.modeTest()));
            }
            return requete.retrieve().body(PaiementMollie.class);
        }, "annulation du paiement");

        if (reponse == null || reponse.status() == null) {
            throw new PrestataireIndisponibleException(
                    "Reponse d annulation sans statut pour " + referencePrestataire + ".");
        }
        return etat(reponse);
    }

    private java.util.Optional<Boolean> modeTestEventuel() {
        return proprietes.exigeContexteOrganisation()
                ? java.util.Optional.of(proprietes.modeTest())
                : java.util.Optional.empty();
    }

    /**
     * Projection des statuts Mollie vers le vocabulaire du domaine.
     *
     * <p>{@code authorized} rejoint EN_COURS et non REUSSI : l'autorisation réserve
     * les fonds sans les capturer. Les traiter comme un encaissement décrémenterait
     * le stock et émettrait une facture pour un montant qui peut encore ne jamais
     * arriver.</p>
     *
     * <p>Un statut inconnu lève plutôt que de tomber sur une valeur par défaut : se
     * rabattre sur ECHOUE annulerait peut-être un paiement réussi, et sur REUSSI
     * livrerait peut-être sans encaissement. L'incertitude doit remonter, pas se
     * résoudre au hasard.</p>
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
        if (proprietes.exigeContexteOrganisation()) {
            // Pas de profileId ici : le Refund se rattache au paiement d'origine, qui
            // porte déjà son profil. Seul le mode reste à préciser.
            corps.put("testmode", proprietes.modeTest());
        }

        RemboursementMollie reponse = appeler(() -> client.post()
                        .uri("/payments/{id}/refunds", demande.referencePrestataire())
                        // Clé DERIVEE du paiement, donc stable au rejeu : c'est elle qui
                        // empêche de rembourser deux fois.
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
        // Un Refund Mollie naît « pending » et ne devient « refunded » qu'après
        // exécution bancaire. Le contrat de PrestatairePaiement suppose une réponse
        // synchrone : l'acceptation du Refund vaut ici engagement. Le passage à
        // l'asynchrone (webhook de remboursement, statut intermédiaire sur paiement)
        // reste pour la V2, et il ne se résout pas dans ce fichier. Le contrat synchrone
        // tient parce que Mollie accepte le Refund sur-le-champ.
        return new RemboursementCree(reponse.id());
    }

    // --- plomberie --------------------------------------------------------------------

    /**
     * Deux décimales et un point décimal, quelle que soit la locale du serveur :
     * Mollie attend une chaîne, et un montant formaté à la belge (« 80,21 ») serait
     * rejeté ou, pire, mal interprété.
     */
    private static String montantMollie(BigDecimal montant) {
        return montant.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Enveloppe tout appel sortant : aucune exception du client HTTP ne franchit
     * cette classe sous sa forme d'origine.
     *
     * <p>Le message conserve l'opération mais jamais le corps de la réponse, qui peut
     * porter des identifiants de requête et des détails d'infrastructure. La cause est
     * attachée pour le journal. Ce qui s'affiche au membre vient de l'i18n.</p>
     *
     * <p><b>Garantie dont dépend la journalisation des échecs</b> (voir les deux
     * {@code catch} de {@code CommandeController}) : aucun message construit dans cette
     * classe ne porte l'identifiant d'accès, ni entier ni tronqué. Les sept sites qui
     * lèvent une {@link PrestataireIndisponibleException} composent des chaînes fixes,
     * au plus complétées du nom de l'opération, du statut rendu par Mollie ou de la
     * référence du paiement : la seule donnée que le journal reprend délibérément par
     * ailleurs. Le message est donc journalisable tel quel. C'est l'affichage à
     * l'écran, lui, qui reste interdit.</p>
     */
    private <T> T appeler(java.util.function.Supplier<T> appel, String operation) {
        try {
            return appel.get();
        } catch (RestClientException echec) {
            throw new PrestataireIndisponibleException(
                    "Echec de l appel au prestataire de paiement (" + operation + ").", echec);
        }
    }

    // --- dialogue Mollie, confiné à cette classe ---------------------------------------

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
    record PaiementMollie(String id, String status, String method, MontantMollie amount,
                          @JsonProperty("isCancelable") Boolean annulable,
                          @JsonProperty("_links") LiensMollie links) {

        /** URL de la page de paiement, absente des paiements déjà aboutis. */
        String urlDePaiement() {
            return links == null || links.checkout() == null ? null : links.checkout().href();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RemboursementMollie(String id, String status, MontantMollie amount) {
    }
}
