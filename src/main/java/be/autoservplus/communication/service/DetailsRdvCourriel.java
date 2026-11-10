package be.autoservplus.communication.service;

import java.time.ZonedDateTime;

/**
 * Elements d un rendez-vous strictement necessaires a la redaction d un courriel.
 *
 * <p>Volontairement independant de l entite {@code Rdv} pour que le module
 * {@code communication} ne depende pas du module {@code reservation}. Le début
 * arrive dans le fuseau de l'atelier, posé par l'appelant qui seul le connaît. Le jour
 * et l'heure se rédigent dans la langue du destinataire, pas dans celle de l'appelant.</p>
 */
public record DetailsRdvCourriel(String numero, ZonedDateTime debut) {
}
