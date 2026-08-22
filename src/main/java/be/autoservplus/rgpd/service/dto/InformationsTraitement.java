package be.autoservplus.rgpd.service.dto;

import java.util.List;

/**
 * Bloc « informations sur le traitement » exige par l article 15, paragraphe 1,
 * du RGPD : finalites, categories de donnees, destinataires, durees de
 * conservation, droits de la personne et moyen de les exercer.
 *
 * <p>Ce bloc ne vient <b>pas</b> de la base : il decrit le traitement, pas le
 * dossier du membre. Il est construit a partir du catalogue de cles i18n
 * ({@code rgpd.export.*}) resolu dans la langue du membre — un rappel legal que
 * la personne ne comprend pas ne remplit pas son office (article 12 : forme
 * concise, transparente et intelligible).
 *
 * <p>Le contenu doit rester aligne sur le livrable 18 (aspects juridiques) et sur
 * la politique de confidentialite publiee : ce sont eux qui font foi, le code n en
 * est que la restitution.
 */
public record InformationsTraitement(
        String responsableTraitement,
        List<Finalite> finalites,
        List<String> categoriesDonnees,
        List<Destinataire> destinataires,
        List<DureeConservation> dureesConservation,
        List<Droit> droits,
        String exerciceDesDroits,
        String note) {

    /** Finalite d un traitement et la base legale qui l autorise (article 6). */
    public record Finalite(String code, String libelle, String baseLegale) {
    }

    /**
     * Destinataire des donnees. Seuls les sous-traitants reellement mobilises par
     * la plateforme figurent ici : annoncer un destinataire fictif serait une
     * information inexacte au sens de l article 15.
     */
    public record Destinataire(String nom, String role, String pays) {
    }

    public record DureeConservation(String donnees, String duree) {
    }

    /** Droit de la personne concernee, avec l article du RGPD qui le fonde. */
    public record Droit(String libelle, String article) {
    }
}
