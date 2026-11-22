package be.autoservplus.config;

import be.autoservplus.common.validation.Iban;
import be.autoservplus.common.validation.NumeroEntrepriseBelge;
import be.autoservplus.identite.domain.Langue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Identification légale du garage, telle qu'elle doit figurer sur une facture belge :
 * dénomination, siège, numéro d'entreprise (BCE) et numéro de TVA sont des mentions
 * obligatoires (Code TVA art. 53, AR n°1 art. 5). La forme juridique et la mention du
 * registre des personnes morales s'y ajoutent pour une société (CSA art. 2:20).
 *
 * <p>En configuration et non en base : la V1 est mono-tenant, ces valeurs sont celles
 * d'un seul garage et ne changent pas à chaud. Chaque champ se lit dans sa variable
 * d'environnement {@code GARAGE_*}. Le passage au multi-tenant (V2) déplacera ces
 * données vers une table portant la clé du garage. C'est alors le fournisseur qui
 * changera, pas le document.</p>
 *
 * <p>Premier usage de {@code @ConfigurationProperties} dans le projet, qui n'employait
 * jusqu'ici que {@code @Value} : quatorze champs liés ensemble valent mieux que quatorze
 * injections indépendantes qu'on peut oublier d'aligner. La liaison ne valide rien. Le
 * contrôle a lieu au démarrage sous le seul profil {@code prod}
 * ({@link #verifierPourProduction()}). Sous {@code dev}, {@code demo} et les tests, les
 * valeurs de démonstration ne le passeraient pas, et c'est voulu.</p>
 *
 * <p>Limite V1 : le siège est supposé à l'adresse de l'atelier. Une seule adresse est
 * configurée, imprimée comme siège sur la facture et dans les mentions légales, et
 * reprise par la page de contact. Un garage dont le siège social est ailleurs devra
 * attendre un champ distinct.</p>
 */
@ConfigurationProperties(prefix = "autoservplus.garage")
public record IdentiteGarage(
        /**
         * Nom commercial, celui sous lequel le garage se presente au public. Distinct de
         * la raison sociale : la mention legale du responsable de la publication nomme
         * l enseigne ET l entite juridique, parce que ce sont deux choses que le lecteur
         * doit pouvoir rapprocher lui-meme. Facultatif : un garage sans enseigne se
         * présente sous sa raison sociale, que {@link #enseigne()} reprend alors.
         */
        String nomCommercial,
        String raisonSociale,
        /**
         * Forme juridique (SRL, SA...), vide pour une personne physique, qui exerce en
         * nom propre et n'en a pas.
         */
        String formeJuridique,
        String rue,
        String numeroRue,
        String codePostal,
        String localite,
        String pays,
        /** Numero d entreprise a la Banque-Carrefour, format 0000.000.000. */
        String numeroBce,
        /**
         * Mention du registre des personnes morales et de son tribunal, telle qu'elle
         * s'imprime (« RPM Bruxelles »). Exigée d'une société, sans objet pour une
         * personne physique.
         */
        String rpm,
        /** Numero de TVA intracommunautaire, format BE0000000000. */
        String numeroTva,
        String iban,
        String telephone,
        String courriel,
        /**
         * Langues dans lesquelles le garage émet ses documents, la première étant celle
         * par défaut ({@code GARAGE_LANGUES_DOCUMENTS}). Elles suivent la région du siège
         * d'exploitation et non le client : néerlandais en Flandre, français en
         * Wallonie, l'une et l'autre à Bruxelles (lois coordonnées du 18 juillet 1966,
         * art. 52 § 1er, décret flamand du 19 juillet 1973, art. 2, décret de la
         * Communauté française du 30 juin 1982). L'anglais n'est admis nulle part,
         * l'allemand n'est pas géré. Règle non validée par un juriste.
         */
        List<Langue> languesDocuments) {

    private static final List<Langue> LANGUES_ADMISES = List.of(Langue.fr, Langue.nl);

    /**
     * Refuse au démarrage, sous tout profil, une langue de document que la loi n'admet
     * pas. Une valeur inconnue n'arrive pas jusqu'ici : la liaison de la configuration
     * l'a déjà rejetée. Sans réglage, le français.
     */
    @ConstructorBinding
    public IdentiteGarage {
        if (languesDocuments == null || languesDocuments.isEmpty()) {
            languesDocuments = List.of(Langue.fr);
        }
        if (!LANGUES_ADMISES.containsAll(languesDocuments)
                || languesDocuments.stream().distinct().count() != languesDocuments.size()) {
            throw new IllegalArgumentException(
                    "GARAGE_LANGUES_DOCUMENTS refusée : une ou deux langues parmi fr et nl, sans répétition, "
                            + "la première étant la langue par défaut. Reçu : " + languesDocuments);
        }
        languesDocuments = List.copyOf(languesDocuments);
    }

    /** Un garage qui émet ses documents en français, valeur par défaut du réglage. */
    public IdentiteGarage(String nomCommercial, String raisonSociale, String formeJuridique, String rue,
                          String numeroRue, String codePostal, String localite, String pays, String numeroBce,
                          String rpm, String numeroTva, String iban, String telephone, String courriel) {
        this(nomCommercial, raisonSociale, formeJuridique, rue, numeroRue, codePostal, localite, pays, numeroBce,
                rpm, numeroTva, iban, telephone, courriel, List.of(Langue.fr));
    }

    /** Langue des documents quand celle du destinataire n'est pas admise, et langue de l'export comptable. */
    public Langue langueParDefaut() {
        return languesDocuments.get(0);
    }

    /**
     * Langue d'un document destiné à ce membre : la sienne si le siège l'admet, sinon la
     * langue par défaut. Un membre anglophone reçoit donc sa facture dans la langue par
     * défaut, et un membre francophone d'un garage flamand la reçoit en néerlandais.
     */
    public Langue langueDuDocument(Langue duMembre) {
        return languesDocuments.contains(duMembre) ? duMembre : langueParDefaut();
    }

    /** Adresse du siege sur une ligne, pour l en-tete du document. */
    public String adresseLisible() {
        return "%s %s, %s %s".formatted(rue, numeroRue, codePostal, localite);
    }

    /** Nom sous lequel le garage se présente : son enseigne, à défaut sa raison sociale. */
    public String enseigne() {
        return estRenseigne(nomCommercial) ? nomCommercial : raisonSociale;
    }

    /** Dénomination suivie de la forme juridique quand il y en a une. */
    public String denomination() {
        return estRenseigne(formeJuridique) ? raisonSociale + " " + formeJuridique : raisonSociale;
    }

    public boolean aUnNomCommercial() {
        return estRenseigne(nomCommercial);
    }

    public boolean aUneFormeJuridique() {
        return estRenseigne(formeJuridique);
    }

    public boolean aUnRpm() {
        return estRenseigne(rpm);
    }

    /**
     * Refuse une identité qui imprimerait des factures fausses ou incomplètes.
     *
     * <p>Les onze mentions obligatoires doivent être présentes, la mention RPM dès
     * qu'une forme juridique est donnée, et les trois numéros doivent passer leur clé
     * de contrôle. Toutes les fautes sont nommées en une fois, par leur variable : un
     * exploitant qui corrige une variable à la fois redémarrerait autant de fois qu'il y
     * a de fautes.</p>
     *
     * <p>Un démarrage refusé vaut mieux que l'alternative. Une variable oubliée laissait
     * la valeur de démonstration sur des factures réelles, une variable vide laissait
     * une mention obligatoire vide, et rien ne le signalait avant qu'un client ne lise
     * sa facture.</p>
     *
     * @throws IllegalStateException avec la liste complète des variables fautives
     */
    public void verifierPourProduction() {
        List<String> fautes = anomalies();
        if (!fautes.isEmpty()) {
            throw new IllegalStateException(
                    "Identité légale du garage refusée, démarrage interrompu. Variables à corriger :\n  - "
                            + String.join("\n  - ", fautes));
        }
    }

    /** Fautes de configuration, une ligne par variable, dans l'ordre des variables. */
    List<String> anomalies() {
        List<String> fautes = new ArrayList<>();
        obligatoires().forEach((variable, valeur) -> {
            if (!estRenseigne(valeur)) {
                fautes.add(variable + " absente ou vide");
            }
        });
        if (aUneFormeJuridique() && !aUnRpm()) {
            fautes.add("GARAGE_RPM absente ou vide, alors que GARAGE_FORME_JURIDIQUE est renseignée");
        }
        if (estRenseigne(numeroBce) && !NumeroEntrepriseBelge.estValide(numeroBce)) {
            fautes.add("GARAGE_BCE invalide : dix chiffres attendus, le premier égal à 0 ou à 1, "
                    + "et une clé modulo 97 juste");
        }
        if (estRenseigne(numeroTva) && !NumeroEntrepriseBelge.tvaCorrespond(numeroTva, numeroBce)) {
            fautes.add("GARAGE_TVA invalide : « BE » suivi des dix chiffres de GARAGE_BCE attendu");
        }
        if (estRenseigne(iban) && !Iban.estValide(iban)) {
            fautes.add("GARAGE_IBAN invalide : clé ISO 13616 fausse, ou clé nationale belge fausse");
        }
        return fautes;
    }

    /**
     * Les mentions sans lesquelles la facture, les mentions légales ou la page de contact
     * s'impriment avec un trou. Restent facultatifs le nom commercial (repli sur la
     * raison sociale), la forme juridique (personne physique) et la mention RPM, exigée
     * seulement avec une forme juridique.
     */
    private Map<String, String> obligatoires() {
        Map<String, String> champs = new LinkedHashMap<>();
        champs.put("GARAGE_RAISON_SOCIALE", raisonSociale);
        champs.put("GARAGE_RUE", rue);
        champs.put("GARAGE_NUMERO_RUE", numeroRue);
        champs.put("GARAGE_CODE_POSTAL", codePostal);
        champs.put("GARAGE_LOCALITE", localite);
        champs.put("GARAGE_PAYS", pays);
        champs.put("GARAGE_BCE", numeroBce);
        champs.put("GARAGE_TVA", numeroTva);
        champs.put("GARAGE_IBAN", iban);
        champs.put("GARAGE_TELEPHONE", telephone);
        champs.put("GARAGE_COURRIEL", courriel);
        return champs;
    }

    private static boolean estRenseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }
}
