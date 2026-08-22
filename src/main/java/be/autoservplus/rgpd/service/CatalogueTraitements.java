package be.autoservplus.rgpd.service;

import be.autoservplus.rgpd.service.dto.ExportDonnees;
import be.autoservplus.rgpd.service.dto.InformationsTraitement;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Catalogue du rappel legal joint a l export (article 15, paragraphe 1) et des
 * notes d exclusion.
 *
 * <p>Le contenu est <b>structurel dans le code, textuel dans l i18n</b> : les
 * listes ci-dessous fixent quels traitements, destinataires, durees et droits sont
 * declares — c est une decision de conformite, elle doit se relire d un coup d oeil
 * et se defendre a l oral — tandis que chaque libelle vit dans
 * {@code i18n/messages*.properties} sous le prefixe {@code rgpd.export.*}. Aucune
 * chaine destinee a la personne n est ecrite en dur ici.
 *
 * <p>La resolution se fait dans la <b>langue du membre</b> et non dans celle de la
 * requete : l article 12 exige une information intelligible pour la personne
 * concernee, et un fichier telecharge se relit longtemps apres la session qui l a
 * produit.
 *
 * <p>Les cles sont resolues sans message par defaut : une cle absente leve
 * {@link org.springframework.context.NoSuchMessageException} plutot que de laisser
 * partir un export ampute d une mention obligatoire.
 */
@Component
public class CatalogueTraitements {

    /**
     * Finalites declarees. Chaque code porte deux cles : le libelle et la base
     * legale de l article 6 qui l autorise.
     */
    private static final List<String> FINALITES = List.of(
            "compte", "reservation", "intervention", "vente",
            "comptabilite", "communication", "marketing", "securite");

    /** Categories de donnees traitees, alignees sur les sections de l export. */
    private static final List<String> CATEGORIES = List.of(
            "identification", "coordonnees", "vehicule", "atelier",
            "commande", "connexion", "consentement");

    /**
     * Sous-traitants effectivement mobilises par la plateforme : Mollie pour le
     * paiement, Brevo pour le courriel, l hebergeur pour l application et la base.
     * Aucun autre n est declare — la liste doit rester le miroir de la realite
     * technique, pas une liste de precaution.
     */
    private static final List<String> DESTINATAIRES = List.of("mollie", "brevo", "hebergeur");

    private static final List<String> CONSERVATIONS = List.of(
            "compte", "comptable", "atelier", "consentement", "connexion");

    /**
     * Droits rappeles. Au-dela des cinq droits materiels, le retrait du
     * consentement (article 7.3) et la reclamation aupres de l autorite de controle
     * (article 77) sont exiges par l article 15.1.e-f.
     */
    private static final List<String> DROITS = List.of(
            "acces", "rectification", "effacement", "limitation",
            "portabilite", "opposition", "retrait", "reclamation");

    private final MessageSource messages;

    public CatalogueTraitements(MessageSource messages) {
        this.messages = messages;
    }

    /** Rappel legal complet, resolu dans la langue demandee. */
    public InformationsTraitement informationsTraitement(Locale langue) {
        return new InformationsTraitement(
                msg("rgpd.export.responsable", langue),
                FINALITES.stream()
                        .map(code -> new InformationsTraitement.Finalite(
                                code,
                                msg("rgpd.export.finalite." + code, langue),
                                msg("rgpd.export.finalite." + code + ".base", langue)))
                        .toList(),
                CATEGORIES.stream()
                        .map(code -> msg("rgpd.export.categorie." + code, langue))
                        .toList(),
                DESTINATAIRES.stream()
                        .map(code -> new InformationsTraitement.Destinataire(
                                msg("rgpd.export.destinataire." + code + ".nom", langue),
                                msg("rgpd.export.destinataire." + code + ".role", langue),
                                msg("rgpd.export.destinataire." + code + ".pays", langue)))
                        .toList(),
                CONSERVATIONS.stream()
                        .map(code -> new InformationsTraitement.DureeConservation(
                                msg("rgpd.export.duree." + code + ".donnees", langue),
                                msg("rgpd.export.duree." + code + ".duree", langue)))
                        .toList(),
                DROITS.stream()
                        .map(code -> new InformationsTraitement.Droit(
                                msg("rgpd.export.droit." + code, langue),
                                msg("rgpd.export.droit." + code + ".article", langue)))
                        .toList(),
                msg("rgpd.export.exercice", langue),
                msg("rgpd.export.note", langue));
    }

    /**
     * Notes d exclusion. Elles sont affirmatives, pas defensives : la note sur la
     * carte bancaire enonce une <b>absence de collecte</b> que le membre ne peut
     * pas deduire d une section vide.
     */
    public ExportDonnees.Exclusions exclusions(Locale langue) {
        return new ExportDonnees.Exclusions(
                msg("rgpd.export.exclusion.mot-de-passe", langue),
                msg("rgpd.export.exclusion.carte", langue),
                msg("rgpd.export.exclusion.secrets", langue));
    }

    private String msg(String cle, Locale langue) {
        return messages.getMessage(cle, null, langue);
    }
}
