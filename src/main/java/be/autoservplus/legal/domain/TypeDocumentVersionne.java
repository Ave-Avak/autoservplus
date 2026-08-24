package be.autoservplus.legal.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Documents dont le texte engageant est versionne et archive (F24).
 *
 * <p>L enumeration ne reprend pas {@code TypeDocumentConsentement} : celui-ci liste
 * les <i>objets consentis</i>, celle-ci les <i>documents</i> dont on gele le texte.
 * Les deux ne coincident pas — {@code COOKIES_ANALYTIQUE} et {@code COOKIES_MARKETING}
 * sont deux consentements distincts recueillis sur un seul et meme document, et
 * {@code POLITIQUE_CONFIDENTIALITE} comme {@code NEWSLETTER} figurent au CHECK de
 * {@code consentement} sans qu aucune ligne ne soit jamais ecrite pour eux. Versionner
 * un document qu on ne fait accepter par personne creerait une version qu aucune preuve
 * ne resout, et laisserait croire l inverse.</p>
 *
 * <h2>Pourquoi les cles sont enumerees et non derivees d un prefixe</h2>
 *
 * <p>Ce qui engage le client, ce sont les clauses — pas le libelle d un bouton ni un
 * {@code aria-label}. Deriver le texte de la famille entiere {@code cookies.*} ferait
 * qu un simple changement de « Tout accepter » en « Accepter tout » publierait une
 * nouvelle version et redemanderait leur consentement a tous les membres : une redemande
 * sans cause, c est-a-dire du bruit qui finit par etre clique sans etre lu.</p>
 *
 * <p>Le risque inverse — une clause ajoutee au gabarit sans etre inscrite ici, donc
 * jamais gelee — est repris par un test qui confronte cette liste a la famille de cles
 * complete et <b>casse la build</b> sur toute cle non classee. Meme raisonnement que
 * {@code fn_tables_traces_audit()} en V28 : la liste est explicite parce qu elle doit
 * s enoncer, et un test la garde de la peremption.</p>
 */
public enum TypeDocumentVersionne {

    /** Conditions generales de vente, acceptees a la conversion du panier (F14). */
    CGV("cgv", "legal.cgv.", List.of(
            "legal.cgv.titre",
            "legal.cgv.intro",
            "legal.cgv.art1.titre", "legal.cgv.art1.corps",
            "legal.cgv.art2.titre", "legal.cgv.art2.corps",
            "legal.cgv.art3.titre", "legal.cgv.art3.corps",
            "legal.cgv.art4.titre", "legal.cgv.art4.corps",
            "legal.cgv.art5.titre", "legal.cgv.art5.corps",
            "legal.cgv.art6.titre", "legal.cgv.art6.corps", "legal.cgv.art6.completer",
            "legal.cgv.art7.titre", "legal.cgv.art7.corps", "legal.cgv.art7.completer",
            "legal.cgv.art8.titre", "legal.cgv.art8.corps",
            "legal.cgv.art9.titre", "legal.cgv.art9.corps",
            "legal.cgv.art10.titre", "legal.cgv.art10.completer",
            "legal.cgv.art11.titre", "legal.cgv.art11.completer",
            "legal.cgv.art12.titre", "legal.cgv.art12.corps",
            "legal.cgv.art13.titre", "legal.cgv.art13.completer")),

    /**
     * Politique cookies presentee au bandeau et a l ecran de gestion (F25). Un seul
     * document, deux finalites consenties separement : les preuves
     * {@code COOKIES_ANALYTIQUE} et {@code COOKIES_MARKETING} portent toutes deux sa
     * version.
     */
    COOKIES("cookies", "cookies.", List.of(
            "cookies.bandeau.titre", "cookies.bandeau.texte",
            "cookies.necessaires.nom", "cookies.necessaires.obligatoire",
            "cookies.necessaires.finalite", "cookies.necessaires.duree",
            "cookies.analytique.nom", "cookies.analytique.activer",
            "cookies.analytique.finalite", "cookies.analytique.duree",
            "cookies.marketing.nom", "cookies.marketing.activer",
            "cookies.marketing.finalite", "cookies.marketing.duree",
            "cookies.note.aucun-traceur", "cookies.note.duree-memorisation",
            "cookies.page.introduction")),

    /**
     * Renonciation au droit de retractation pour un service pleinement executé
     * (F12, art. VI.53 CDE). Le texte engage a lui seul la perte d un droit : c est
     * celui des trois pour lequel l ecart entre « accepte » et « a lu quoi » coute le
     * plus cher.
     */
    RENONCIATION_RETRACTATION("renonciation", "commande.vi53.", List.of(
            "commande.vi53.libelle",
            "commande.vi53.explication"));

    private final String slug;
    private final String famille;
    private final List<String> cles;

    TypeDocumentVersionne(String slug, String famille, List<String> cles) {
        this.slug = slug;
        this.famille = famille;
        this.cles = cles;
    }

    /**
     * Segment d URL du document a la consultation d archive. Distinct du nom de
     * l enumeration : une adresse publique et durable — elle figurera dans des preuves
     * citees pendant des annees — ne doit pas suivre les renommages du code.
     */
    public String slug() {
        return slug;
    }

    /** Cle du libelle traduit du document. */
    public String cleLibelle() {
        return "legal.document." + slug;
    }

    public static Optional<TypeDocumentVersionne> parSlug(String slug) {
        if (slug == null) {
            return Optional.empty();
        }
        String recherche = slug.toLowerCase(Locale.ROOT);
        for (TypeDocumentVersionne type : values()) {
            if (type.slug.equals(recherche)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** Prefixe des cles de messages surveillees par la garde d exhaustivite. */
    public String famille() {
        return famille;
    }

    /**
     * Cles de messages composant le texte engageant, <b>dans l ordre de lecture</b> —
     * l ordre fait partie du gele : deux textes aux memes clauses presentees dans un
     * ordre different ne sont pas le meme document.
     */
    public List<String> cles() {
        return cles;
    }
}
