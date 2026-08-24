package be.autoservplus.legal.service;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.domain.VersionDocument;
import be.autoservplus.legal.repository.VersionDocumentRepository;
import be.autoservplus.legal.service.dto.TexteArchiveVue;
import be.autoservplus.legal.service.dto.VersionEnVigueurVue;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolution des versions de documents engageants (F24).
 *
 * <p>Ce service remplace trois constantes {@code *_VERSION_COURANTE} qui vivaient dans
 * l entite {@code Consentement}. La difference n est pas de style : avec une constante,
 * publier une nouvelle redaction supposait de <b>recompiler et redeployer</b>, et rien
 * n obligeait a le faire — on pouvait modifier le texte sans toucher la constante, et
 * les acceptations passees continuaient a paraitre couvrir la nouvelle version. Avec
 * une table, publier c est inserer des lignes, et le texte de chaque version reste
 * consultable indefiniment.</p>
 *
 * <p><b>Lecture seule.</b> Aucune methode n ecrit : l amorcage est fait par la migration
 * V33, et une publication ulterieure passera par une migration elle aussi. Une archive
 * legale ecrite par du code applicatif serait modifiable par du code applicatif.</p>
 */
@Service
@Transactional(readOnly = true)
public class VersionsDocumentsService {

    private final VersionDocumentRepository versions;
    private final Clock horloge;

    public VersionsDocumentsService(VersionDocumentRepository versions, Clock horloge) {
        this.versions = versions;
        this.horloge = horloge;
    }

    /**
     * Identifiant de la version en vigueur, a figer sur une preuve de consentement.
     *
     * <p>Absence traitee comme une erreur et non comme un defaut silencieux : ecrire une
     * preuve sans version reviendrait a enregistrer que le membre a accepte « quelque
     * chose ». Mieux vaut refuser la commande que produire une preuve qui ne prouve
     * rien — et l absence ne peut venir que d une base incompletement migree, jamais
     * d une saisie utilisateur.</p>
     */
    public String versionCourante(TypeDocumentVersionne type) {
        return versionEnVigueur(type)
                .map(VersionEnVigueurVue::version)
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune version en vigueur pour le document " + type
                                + " : la table version_document n a pas ete amorcee (V33)."));
    }

    /** Version en vigueur et sa date d entree en vigueur, pour l affichage public. */
    public Optional<VersionEnVigueurVue> versionEnVigueur(TypeDocumentVersionne type) {
        return versions.versionsEnVigueur(type, horloge.instant(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .flatMap(version -> texteDe(type, version, Langue.fr)
                        .map(ligne -> new VersionEnVigueurVue(ligne.getVersion(), ligne.getDateEffet())));
    }

    /**
     * Texte archive d une version donnee, dans la langue demandee.
     *
     * <p>La langue demandee prime, mais l absence ne rend pas 404 : une version publiee
     * avant l ajout d une langue n existe pas dans celle-ci, et repondre « introuvable »
     * a qui cherche un texte qui existe bel et bien serait faux. On sert alors le
     * francais — langue par defaut du projet — en annoncant la langue reellement
     * servie.</p>
     */
    public Optional<TexteArchiveVue> archive(TypeDocumentVersionne type, String version, Locale locale) {
        List<VersionDocument> lignes = versions.findByTypeDocumentAndVersionOrderByLangue(type, version);
        if (lignes.isEmpty()) {
            return Optional.empty();
        }
        Langue demandee = langueDe(locale);
        VersionDocument servie = lignes.stream()
                .filter(ligne -> ligne.getLangue() == demandee)
                .findFirst()
                .or(() -> lignes.stream().filter(ligne -> ligne.getLangue() == Langue.fr).findFirst())
                .orElse(lignes.getFirst());

        return Optional.of(new TexteArchiveVue(
                servie.getVersion(), servie.getDateEffet(), servie.getLangue().name(),
                servie.getContenu(), servie.isActif(),
                lignes.stream().map(ligne -> ligne.getLangue().name()).toList()));
    }

    private Optional<VersionDocument> texteDe(TypeDocumentVersionne type, String version, Langue langue) {
        return versions.findByTypeDocumentAndVersionAndLangue(type, version, langue);
    }

    /**
     * Toute locale hors {fr, nl, en} retombe sur le francais, comme
     * {@code ResolveurLangueSession} le fait cote web : l ensemble des langues servies
     * est ferme, et il doit l etre au meme endroit dans les deux sens.
     */
    private Langue langueDe(Locale locale) {
        if (locale == null) {
            return Langue.fr;
        }
        for (Langue langue : Langue.values()) {
            if (langue.name().equalsIgnoreCase(locale.getLanguage())) {
                return langue;
            }
        }
        return Langue.fr;
    }
}
