package be.autoservplus.identite.domain;

/**
 * Objets dont l acceptation est tracee dans la table {@code consentement}.
 * Les valeurs collent au CHECK {@code ck_consentement_type} — le schema en base
 * fait foi. Le dictionnaire evoque en plus un type RETRAIT que le CHECK n admet
 * pas : en base reelle, le retrait se modelise par une nouvelle ligne
 * {@code accorde = false}, pas par un type dedie (divergence consignee).
 */
public enum TypeDocumentConsentement {
    CGV,
    POLITIQUE_CONFIDENTIALITE,

    /**
     * Type generique du socle V1, conserve par le CHECK mais plus employe : depuis
     * F25 le consentement aux cookies se prouve <b>finalite par finalite</b>, un
     * booleen unique ne pouvant pas restituer un choix personnalise.
     */
    COOKIES,

    NEWSLETTER,

    /**
     * Mesure d audience. Finalite optionnelle : refusee par defaut, elle n est
     * accordee que par un geste explicite (aucune case pre-cochee — exigence de
     * l Autorite de Protection des Donnees).
     */
    COOKIES_ANALYTIQUE,

    /** Publicite ciblee. Finalite optionnelle, refusee par defaut comme la precedente. */
    COOKIES_MARKETING,

    /**
     * Renonciation au droit de retractation pour un service pleinement execute avant
     * la fin des quatorze jours (F12, art. VI.53 CDE). C est la PREUVE ; l etat lu par
     * F30 pour decider est {@code commande.renonciation_vi53}.
     */
    RENONCIATION_RETRACTATION
}
