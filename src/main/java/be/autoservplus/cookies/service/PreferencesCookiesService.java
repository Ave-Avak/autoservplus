package be.autoservplus.cookies.service;

import be.autoservplus.cookies.domain.PreferencesCookies;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.service.VersionsDocumentsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Enregistrement du choix de l utilisateur sur les cookies (F25).
 *
 * <p><b>Deux supports, deux roles.</b> Le cookie de preference porte l <i>etat
 * courant</i> et sert a decider, a chaque page, s il faut reposer la question. La
 * table {@code consentement} porte l <i>historique</i> : une ligne par finalite et
 * par decision, jamais modifiee. Le premier se remplace, la seconde s empile — ce
 * n est pas une redondance, ce sont deux besoins differents. Un cookie efface par
 * le navigateur ne doit pas emporter la preuve avec lui.</p>
 *
 * <p><b>Le visiteur non connecte ne laisse aucune trace en base.</b> La table exige
 * un titulaire ({@code utilisateur_id NOT NULL}), et il n y aurait de toute facon
 * rien a rattacher : creer une ligne pour un anonyme supposerait de l identifier
 * d une maniere ou d une autre, c est-a-dire de collecter une donnee personnelle
 * pour prouver qu il a refuse d etre suivi. Son choix vit dans son navigateur, ce
 * qui est exactement le principe de minimisation (art. 5.1.c RGPD).</p>
 */
@Service
public class PreferencesCookiesService {

    /**
     * Duree de memorisation du choix, imposee par le cahier des charges (P412) et
     * alignee sur la recommandation de l Autorite de Protection des Donnees. Passe
     * ce delai le bandeau reapparait : un consentement ne vaut pas indefiniment.
     */
    private static final int MOIS_DE_MEMORISATION = 6;

    private final UtilisateurRepository utilisateurs;
    private final ConsentementRepository consentements;
    private final VersionsDocumentsService versionsDocuments;
    private final Clock horloge;

    public PreferencesCookiesService(UtilisateurRepository utilisateurs,
                                     ConsentementRepository consentements,
                                     VersionsDocumentsService versionsDocuments,
                                     Clock horloge) {
        this.utilisateurs = utilisateurs;
        this.consentements = consentements;
        this.versionsDocuments = versionsDocuments;
        this.horloge = horloge;
    }

    /**
     * Consigne la decision d un membre connecte : une ligne par finalite
     * optionnelle, accordee ou refusee.
     *
     * <p>Les deux lignes portent le <b>meme instant</b>, pris une seule fois : elles
     * decrivent un geste unique, et deux horodatages voisins mais differents
     * laisseraient croire a deux decisions successives.</p>
     *
     * <p>Aucune ligne n est ecrite pour les cookies strictement necessaires : ils ne
     * se refusent pas, donc ils ne se consentent pas.</p>
     *
     * @param email      titulaire du choix, {@code null} pour un visiteur non
     *                   connecte — le choix reste alors dans son seul navigateur
     * @param adresseIp  adresse de la requete, conservee comme element de preuve ;
     *                   peut etre absente
     */
    @Transactional
    public void enregistrer(String email, PreferencesCookies preferences, String adresseIp) {
        if (email == null || email.isBlank()) {
            return;
        }
        Optional<Utilisateur> titulaire = utilisateurs.findByEmailIgnoreCase(email);
        if (titulaire.isEmpty()) {
            // Compte disparu entre l affichage de la page et l envoi du formulaire
            // (suppression de compte, session survivante) : le cookie a deja ete pose
            // par le controleur, le choix est donc respecte cote navigateur. Rien a
            // rattacher en base, et surtout pas d erreur a montrer pour un bandeau.
            return;
        }
        Instant maintenant = horloge.instant();
        // Une seule resolution pour les deux lignes, comme pour l instant : les deux
        // finalites sont consenties sur UN document unique, et deux lectures pourraient
        // en theorie encadrer une publication et attribuer au meme geste deux versions
        // differentes.
        String version = versionsDocuments.versionCourante(TypeDocumentVersionne.COOKIES);
        consentements.save(preuve(titulaire.get(), TypeDocumentConsentement.COOKIES_ANALYTIQUE,
                preferences.analytique(), adresseIp, maintenant, version));
        consentements.save(preuve(titulaire.get(), TypeDocumentConsentement.COOKIES_MARKETING,
                preferences.marketing(), adresseIp, maintenant, version));
    }

    /**
     * Duree de vie du cookie de preference, calculee sur l horloge injectee plutot
     * qu approchee par un nombre de jours fixe : six mois calendaires n ont pas tous
     * la meme longueur, et la duree annoncee a l utilisateur doit etre celle qui
     * s applique.
     */
    public Duration dureeDeMemorisation() {
        Instant maintenant = horloge.instant();
        return Duration.between(maintenant,
                maintenant.atZone(horloge.getZone()).plusMonths(MOIS_DE_MEMORISATION).toInstant());
    }

    private Consentement preuve(Utilisateur titulaire, TypeDocumentConsentement finalite,
                                boolean accorde, String adresseIp, Instant maintenant,
                                String version) {
        return Consentement.decision(titulaire, finalite, version, accorde, adresseIp, maintenant);
    }
}
