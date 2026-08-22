package be.autoservplus.identite.domain;

/**
 * Documents dont l acceptation est tracee dans la table {@code consentement}.
 * Les valeurs collent au CHECK {@code ck_consentement_type} de V1 — le schema en
 * base fait foi. Le dictionnaire evoque en plus un type RETRAIT que le CHECK
 * n admet pas : en base reelle, le retrait se modelise par une nouvelle ligne
 * {@code accorde = false}, pas par un type dedie (divergence consignee).
 */
public enum TypeDocumentConsentement {
    CGV,
    POLITIQUE_CONFIDENTIALITE,
    COOKIES,
    NEWSLETTER
}
