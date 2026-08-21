package be.autoservplus.communication.service;

/**
 * Elements d un rendez-vous strictement necessaires a la redaction d un courriel.
 *
 * <p>Volontairement independant de l entite {@code Rdv} pour que le module
 * {@code communication} ne depende pas du module {@code reservation} : les champs
 * sont deja formates par l appelant, qui seul connait le fuseau et la locale.</p>
 */
public record DetailsRdvCourriel(String numero, String jourLisible, String heureLisible) {
}
