package be.autoservplus.identite.domain;

import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.context.MessageSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Compte de la plateforme AutoServ+.
 *
 * <p>Heritage a table unique : membres et administrateurs partagent la table
 * {@code utilisateur}, distingues par la colonne {@code type_utilisateur}. Ce choix est
 * documente au chapitre 4.1 du schema de base de donnees : les deux profils partagent
 * l essentiel de leurs attributs, et le nombre de colonnes propres a l administrateur
 * est trop faible pour justifier une table dediee.</p>
 */
@Entity
@Table(name = "utilisateur")
@SQLRestriction("deleted_at IS NULL")
public class Utilisateur extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant public, expose dans les URL a la place de la cle primaire. */
    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_utilisateur", nullable = false, length = 20)
    private TypeUtilisateur typeUtilisateur;

    @NotBlank
    @Email
    @Size(max = 180)
    @Column(name = "email", nullable = false, length = 180, unique = true)
    private String email;

    /** Empreinte BCrypt, facteur de cout 12. Le mot de passe en clair n existe jamais en base. */
    @Column(name = "mot_de_passe_hache", nullable = false, length = 60)
    private String motDePasseHache;

    @NotBlank
    @Size(max = 80)
    @Column(name = "nom", nullable = false, length = 80)
    private String nom;

    @NotBlank
    @Size(max = 80)
    @Column(name = "prenom", nullable = false, length = 80)
    private String prenom;

    @Size(max = 30)
    @Column(name = "telephone", length = 30)
    private String telephone;

    @Size(max = 150)
    @Column(name = "rue", length = 150)
    private String rue;

    @Size(max = 15)
    @Column(name = "numero_rue", length = 15)
    private String numeroRue;

    @Size(max = 10)
    @Column(name = "code_postal", length = 10)
    private String codePostal;

    @Size(max = 100)
    @Column(name = "localite", length = 100)
    private String localite;

    @Column(name = "pays", nullable = false, length = 60)
    private String pays = "Belgique";

    @Enumerated(EnumType.STRING)
    @Column(name = "langue", nullable = false, length = 2)
    private Langue langue = Langue.fr;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private StatutUtilisateur statut = StatutUtilisateur.EN_ATTENTE_VALIDATION;

    @Column(name = "email_verifie", nullable = false)
    private boolean emailVerifie = false;

    @Column(name = "jeton_verification", length = 64)
    private String jetonVerification;

    @Column(name = "jeton_expiration")
    private Instant jetonExpiration;

    /**
     * Nouvelle adresse demandee et <b>pas encore prouvee</b> (CdC 5.2.2). L adresse en
     * vigueur reste {@link #email} jusqu a la confirmation : appliquer d abord et
     * verifier ensuite enfermerait hors de son compte tout membre qui se trompe d une
     * lettre, puisque plus aucune adresse joignable ne le rattacherait a sa ligne.
     */
    @Column(name = "email_en_attente", length = 180)
    private String emailEnAttente;

    /**
     * Jeton du lien de confirmation, <b>distinct</b> de {@link #jetonVerification}.
     * Ce dernier ouvre aussi la reinitialisation du mot de passe : un jeton commun
     * serait envoye a une adresse non encore prouvee et permettrait a son porteur de
     * fixer le mot de passe du compte.
     */
    @Column(name = "jeton_changement_email", length = 64)
    private String jetonChangementEmail;

    @Column(name = "jeton_changement_expiration")
    private Instant jetonChangementExpiration;

    @Column(name = "derniere_connexion")
    private Instant derniereConnexion;

    @Column(name = "tentatives_echouees", nullable = false)
    private short tentativesEchouees = 0;

    @Column(name = "verrouille_jusqu_a")
    private Instant verrouilleJusquA;

    /** Fonction occupee dans le garage. Renseigne uniquement pour les administrateurs. */
    @Size(max = 80)
    @Column(name = "fonction", length = 80)
    private String fonction;

    /**
     * Horodatage de l anonymisation (F23, art. 17 RGPD). Marqueur d etat, <b>pas</b>
     * une suppression logique : {@code deleted_at} reste vide pour que la ligne
     * demeure jointe par les factures conservees.
     */
    @Column(name = "anonymise_le")
    private Instant anonymiseLe;

    /**
     * Le mot de passe en vigueur n a pas ete choisi par le titulaire : celui du compte
     * exploitant vient d un fichier {@code .env}, celui d un administrateur cree au
     * back-office n existe pas encore. Tant qu il est pose, la connexion s arrete a
     * l ecran de changement.
     */
    @Column(name = "doit_changer_mot_de_passe", nullable = false)
    private boolean doitChangerMotDePasse;

    /** Secret TOTP chiffre (AES-256-GCM). Ne sort jamais du service du second facteur. */
    @Column(name = "mfa_secret_chiffre")
    private byte[] mfaSecretChiffre;

    @Column(name = "mfa_cle_version")
    private Short mfaCleVersion;

    /** Nul avec un secret present : enrolement commence, pas encore confirme par un code. */
    @Column(name = "mfa_active_le")
    private Instant mfaActiveLe;

    @Column(name = "mfa_dernier_pas")
    private Long mfaDernierPas;

    @Column(name = "mfa_echecs", nullable = false)
    private short mfaEchecs;

    @Column(name = "mfa_verrouillages", nullable = false)
    private short mfaVerrouillages;

    /** Empreinte SHA-256 du jeton de definition. Le jeton en clair ne vit que dans le lien. */
    @Column(name = "jeton_definition_hache", length = 64)
    private String jetonDefinitionHache;

    @Column(name = "jeton_definition_expiration")
    private Instant jetonDefinitionExpiration;

    protected Utilisateur() {
        // requis par JPA
    }

    public Utilisateur(String email, String motDePasseHache, String nom, String prenom,
                       TypeUtilisateur typeUtilisateur) {
        this.reference = UUID.randomUUID();
        this.email = Objects.requireNonNull(email, "email");
        this.motDePasseHache = Objects.requireNonNull(motDePasseHache, "motDePasseHache");
        this.nom = Objects.requireNonNull(nom, "nom");
        this.prenom = Objects.requireNonNull(prenom, "prenom");
        this.typeUtilisateur = Objects.requireNonNull(typeUtilisateur, "typeUtilisateur");
    }

    // --- comportements metier -------------------------------------------------------

    /** Active le compte apres verification de l adresse de courriel. */
    public void confirmerAdresseEmail() {
        this.emailVerifie = true;
        this.statut = StatutUtilisateur.ACTIF;
        this.jetonVerification = null;
        this.jetonExpiration = null;
    }

    public void enregistrerJetonVerification(String jeton, Instant expiration) {
        this.jetonVerification = jeton;
        this.jetonExpiration = expiration;
    }

    public boolean jetonEstExpire(Instant maintenant) {
        return jetonExpiration == null || maintenant.isAfter(jetonExpiration);
    }

    /**
     * Enregistre une demande de changement d adresse, sans rien appliquer.
     *
     * <p>Remplace une demande precedente encore en cours : le dernier choix du membre
     * fait foi, et le lien deja parti cesse d etre exploitable. C est voulu : laisser
     * deux liens vivants ferait dependre l adresse finale de l ordre des clics.</p>
     */
    public void demanderChangementEmail(String nouvelleAdresse, String jeton, Instant expiration) {
        this.emailEnAttente = Objects.requireNonNull(nouvelleAdresse, "nouvelleAdresse");
        this.jetonChangementEmail = Objects.requireNonNull(jeton, "jeton");
        this.jetonChangementExpiration = Objects.requireNonNull(expiration, "expiration");
    }

    public boolean jetonChangementExpire(Instant maintenant) {
        return jetonChangementExpiration == null
                || maintenant.isAfter(jetonChangementExpiration);
    }

    /**
     * Bascule sur l adresse confirmee.
     *
     * <p>{@code emailVerifie} passe a vrai sans autre controle : le clic sur un lien
     * recu a cette adresse EST la preuve qu elle est joignable et controlee. C est le
     * meme raisonnement qu a l activation du compte.</p>
     *
     * @return l ancienne adresse, que l appelant doit avoir capturee pour l aviser :
     *         apres cet appel, la ligne ne la porte plus nulle part
     */
    public String appliquerChangementEmail() {
        if (emailEnAttente == null) {
            throw new IllegalStateException("Aucun changement d adresse en attente.");
        }
        String ancienne = this.email;
        this.email = emailEnAttente;
        this.emailVerifie = true;
        annulerChangementEmail();
        return ancienne;
    }

    public void annulerChangementEmail() {
        this.emailEnAttente = null;
        this.jetonChangementEmail = null;
        this.jetonChangementExpiration = null;
    }

    public boolean changementEmailEnCours() {
        return emailEnAttente != null;
    }

    /**
     * Appelee a la fin de la DERNIERE etape de connexion, jamais apres le seul mot de
     * passe : remettre les compteurs a zero des la premiere etape offrirait un budget
     * d essais neuf a qui ne detient que le mot de passe.
     */
    public void enregistrerConnexionReussie(Instant maintenant) {
        this.derniereConnexion = maintenant;
        this.tentativesEchouees = 0;
        this.verrouilleJusquA = null;
        this.mfaEchecs = 0;
        this.mfaVerrouillages = 0;
    }

    /** Incremente le compteur d echecs et verrouille le compte au seuil atteint. */
    public void enregistrerEchecConnexion(int seuil, Instant verrouillageJusqu) {
        this.tentativesEchouees++;
        if (this.tentativesEchouees >= seuil) {
            this.verrouilleJusquA = verrouillageJusqu;
        }
    }

    /** Verrouille immediatement le compte jusqu a la date indiquee. */
    public void verrouillerJusqu(Instant echeance) {
        this.verrouilleJusquA = echeance;
    }

    public boolean estVerrouille(Instant maintenant) {
        return verrouilleJusquA != null && maintenant.isBefore(verrouilleJusquA);
    }

    /**
     * Applique les champs modifiables du profil.
     *
     * <p>Une methode de domaine plutot que des accesseurs : {@code nom} et
     * {@code prenom} n en avaient aucun, et leur en donner aurait ouvert l entite a
     * des modifications partielles depuis n importe quelle couche. Ici l ensemble
     * change d un coup, ou pas du tout.</p>
     *
     * <p><b>Ce qui n est PAS modifiable ici</b> : l adresse de courriel, qui est
     * l identifiant de connexion et suit une procedure de verification propre. Le
     * statut, qui releve de l administration. Le type d utilisateur, qui ne change
     * jamais.</p>
     *
     * <p>L adresse postale est <b>complete ou vide</b>. La garde est portee par le
     * formulaire, qui peut dire a l utilisateur ce qui manque. L entite accepte les
     * deux etats parce que le schema les accepte : les colonnes sont nullables depuis
     * le socle et des comptes anciens n ont jamais renseigne d adresse.</p>
     */
    public void modifierProfil(String prenom, String nom, String telephone,
                               String rue, String numeroRue, String codePostal,
                               String localite, String pays, Langue langue) {
        this.prenom = normaliser(prenom);
        this.nom = normaliser(nom);
        this.telephone = normaliser(telephone);
        this.rue = normaliser(rue);
        this.numeroRue = normaliser(numeroRue);
        this.codePostal = normaliser(codePostal);
        this.localite = normaliser(localite);
        this.pays = normaliser(pays);
        this.langue = langue;
    }

    /**
     * Vide devient {@code null} : la base distingue les deux, pas l utilisateur. Sans
     * cela, un champ efface au formulaire vaudrait la chaine vide, et
     * {@code adresseLisible()} de la facture (qui teste {@code null}) imprimerait
     * une adresse faite d espaces.
     */
    private static String normaliser(String valeur) {
        if (valeur == null) {
            return null;
        }
        String propre = valeur.trim();
        return propre.isEmpty() ? null : propre;
    }

    /** Les trois champs que la facture exige pour imprimer une adresse. */
    public boolean adressePostaleComplete() {
        return rue != null && codePostal != null && localite != null;
    }

    public boolean estActif() {
        return statut == StatutUtilisateur.ACTIF;
    }

    /**
     * Le compte relève-t-il du back-office ?
     *
     * <p><b>Couvre les DEUX natures privilegiees</b>, et ce n est pas un detail de
     * confort : l unique appelant de ce predicat est la garde de {@link #anonymiser}
     * qui refuse d effacer un compte dont les decisions sont tracees. Le limiter a
     * {@code ADMINISTRATEUR} rendrait un super-administrateur <b>anonymisable</b>,
     * c est-a-dire exactement l inverse de ce que la garde protege : ses decisions
     * sont les plus engageantes du systeme, puisqu il cree et suspend les autres
     * administrateurs. Le piege est etroit et silencieux : ajouter une valeur a
     * l enumeration n aurait rien casse, la garde aurait simplement cesse de couvrir
     * le cas le plus sensible.</p>
     */
    public boolean estAdministrateur() {
        return typeUtilisateur.estPrivilegie();
    }

    /** Pouvoirs exclusifs sur les comptes administrateurs (CdC 5.2.3). */
    public boolean estSuperAdministrateur() {
        return typeUtilisateur == TypeUtilisateur.SUPER_ADMINISTRATEUR;
    }

    public String nomComplet() {
        return prenom + " " + nom;
    }

    /**
     * Nom affichable dans la langue d un document.
     *
     * <p>Un compte anonymise rend le marqueur <b>traduit</b>. Un compte ordinaire rend
     * son vrai nom, qui ne se traduit pas. Utile partout ou le document a une langue
     * propre : une facture est emise dans celle du client, pas dans celle de la session
     * qui la telecharge, et le PDF regenere apres anonymisation doit suivre la meme
     * regle. Un client neerlandophone ne doit pas recevoir « Client supprime ».</p>
     *
     * <p>Le {@link MessageSource} arrive en <b>parametre</b> et non en champ : l entite
     * reste un POJO, sans dependance vers le contexte Spring. C est l appelant, qui
     * connait deja la langue du document, qui fournit les deux.</p>
     */
    public String nomComplet(MessageSource messages, Locale locale) {
        return estAnonymise()
                ? messages.getMessage(CLE_MARQUEUR_ANONYME, null, locale)
                : nomComplet();
    }

    // --- anonymisation (F23, article 17 RGPD) ---------------------------------------

    /**
     * Prenom et nom stockes pour un compte anonymise. Ils composent le marqueur dans la
     * langue <b>par defaut</b> du projet (le francais), qui est celle du back-office :
     * {@code nomComplet()} rend « Client supprimé ». Les documents multilingues, eux,
     * passent par {@link #nomComplet(MessageSource, Locale)} et resolvent
     * {@value #CLE_MARQUEUR_ANONYME}.
     *
     * <p>Un test verifie que ces deux constantes composent exactement la valeur
     * francaise de la cle : les deux representations du marqueur ne peuvent pas
     * diverger sans casser la build.</p>
     */
    public static final String PRENOM_ANONYME = "Client";
    public static final String NOM_ANONYME = "supprimé";

    /** Cle i18n du marqueur, pour les documents qui ont une langue propre. */
    public static final String CLE_MARQUEUR_ANONYME = "compte.anonyme.nom-complet";

    /**
     * Pays d un compte anonymise. Marqueur d absence, et non un pays de substitution :
     * reecrire « Belgique » sur le dossier d une personne qui resida ailleurs
     * n anonymise pas, cela <b>affirme</b> une donnee peut-etre fausse. La colonne est
     * NOT NULL, d ou un marqueur plutot que NULL.
     *
     * <p>Valeur stockee et jamais affichee : l adresse etant videe, aucun document ne
     * l imprime. Elle reste le tiret long, ecrit en sequence d echappement, pour que les
     * comptes deja anonymises et les suivants portent le meme marqueur.</p>
     */
    public static final String PAYS_ANONYME = "\u2014";

    /**
     * Vide le compte de toute donnee personnelle et le marque anonymise.
     *
     * <p><b>La ligne survit.</b> {@code deleted_at} n est deliberement pas renseigne :
     * le {@code @SQLRestriction} de cette entite masquerait sinon la ligne de toutes
     * les requetes, et une facture conservee dix ans ne pourrait plus resoudre son
     * titulaire. L anonymisation vide, elle ne fait pas disparaitre.</p>
     *
     * <p><b>Les champs NOT NULL recoivent un marqueur, pas du vide.</b> {@code nom} et
     * {@code prenom} portent {@code @NotBlank} : une chaine vide echouerait a la
     * validation. Le couple retenu compose « Client supprime » par
     * {@link #nomComplet()}, ce qui est exactement ce qu affichent les ecrans du
     * back-office. {@code pays} recoit un marqueur d <b>absence</b> et non un pays de
     * substitution : y remettre « Belgique » n anonymiserait pas, cela affirmerait une
     * residence peut-etre fausse sur le dossier d une personne qui ne peut plus la
     * corriger.</p>
     *
     * <p><b>Le hachage reste un vrai BCrypt.</b> L appelant fournit l empreinte d un
     * secret aleatoire jete aussitot : la colonne fait 60 caracteres et la
     * verification du mot de passe passe par l encodeur, une constante hors format
     * ferait echouer la comparaison sur une exception au lieu d un refus propre.
     * Aucune connexion n est possible, et personne ne connait le secret.</p>
     *
     * <p>Les traces de connexion (derniere connexion, tentatives, verrouillage) sont
     * effacees avec le reste : ce sont des donnees comportementales, pas des donnees
     * comptables.</p>
     *
     * @param jetonEmail     adresse de substitution, unique et non routable
     * @param hachageInerte  empreinte BCrypt d un secret aleatoire perdu
     * @throws IllegalStateException si le compte est deja anonymise, ou s il s agit
     *         d un administrateur, F23 est un droit du membre sur son propre compte,
     *         et anonymiser un administrateur romprait la tracabilite des decisions
     *         qu il a signees (validations de retractation, historiques de catalogue)
     */
    public void anonymiser(String jetonEmail, String hachageInerte, Instant maintenant) {
        if (anonymiseLe != null) {
            throw new IllegalStateException("Ce compte est deja anonymise.");
        }
        if (estAdministrateur()) {
            throw new IllegalStateException(
                    "Un compte administrateur ne s anonymise pas : ses decisions sont tracees.");
        }
        this.email = Objects.requireNonNull(jetonEmail, "jetonEmail");
        this.motDePasseHache = Objects.requireNonNull(hachageInerte, "hachageInerte");
        this.anonymiseLe = Objects.requireNonNull(maintenant, "maintenant");
        this.prenom = PRENOM_ANONYME;
        this.nom = NOM_ANONYME;
        this.telephone = null;
        this.rue = null;
        this.numeroRue = null;
        this.codePostal = null;
        this.localite = null;
        this.pays = PAYS_ANONYME;
        this.statut = StatutUtilisateur.SUPPRIME;
        this.emailVerifie = false;
        this.jetonVerification = null;
        this.jetonExpiration = null;
        // Une demande de changement porte une SECONDE adresse personnelle, tout aussi
        // identifiante que celle qu on efface : la laisser subsister viderait
        // l anonymisation de son objet.
        annulerChangementEmail();
        this.derniereConnexion = null;
        this.tentativesEchouees = 0;
        this.verrouilleJusquA = null;
        this.fonction = null;
        // Le secret TOTP n identifie personne, mais un compte anonymise ne doit plus
        // rien porter qui serve a s y authentifier.
        retirerSecondFacteur();
        this.mfaEchecs = 0;
        this.mfaVerrouillages = 0;
        this.doitChangerMotDePasse = false;
        this.jetonDefinitionHache = null;
        this.jetonDefinitionExpiration = null;
    }

    public boolean estAnonymise() {
        return anonymiseLe != null;
    }

    public void changerMotDePasse(String nouvelleEmpreinte) {
        this.motDePasseHache = Objects.requireNonNull(nouvelleEmpreinte, "nouvelleEmpreinte");
        this.tentativesEchouees = 0;
        this.verrouilleJusquA = null;
        // Un mot de passe choisi par le titulaire, quel que soit le chemin (changement
        // impose, reinitialisation par courriel), leve l obligation : elle ne visait que
        // le secret qu il n avait pas choisi.
        this.doitChangerMotDePasse = false;
        // Reprendre la main sur son compte doit abandonner toute demande de changement
        // d adresse en cours : c est le geste qu on conseille au membre avise d une
        // demande qu il n a pas faite, et il serait vain s il laissait vivre le lien
        // deja parti vers la boite de l attaquant.
        annulerChangementEmail();
    }
    /**
     * Redefinit le mot de passe et leve le verrouillage.
     *
     * <p>Une personne qui prouve controler la boite de reception n est pas l attaquant
     * que le verrouillage visait a arreter.</p>
     */
    public void reinitialiserMotDePasse(String nouvelleEmpreinte) {
        changerMotDePasse(nouvelleEmpreinte);
        this.jetonVerification = null;
        this.jetonExpiration = null;
        this.emailVerifie = true;
        if (this.statut == StatutUtilisateur.EN_ATTENTE_VALIDATION) {
            this.statut = StatutUtilisateur.ACTIF;
        }
    }

    // --- changement impose et definition du mot de passe ----------------------------

    public void exigerChangementMotDePasse() {
        this.doitChangerMotDePasse = true;
    }

    public boolean doitChangerMotDePasse() {
        return doitChangerMotDePasse;
    }

    /**
     * Arme le lien de definition d un administrateur cree au back-office. Remplace un
     * jeton precedent : renvoyer le lien invalide celui deja parti.
     */
    public void preparerDefinitionMotDePasse(String empreinteJeton, Instant expiration) {
        this.jetonDefinitionHache = Objects.requireNonNull(empreinteJeton, "empreinteJeton");
        this.jetonDefinitionExpiration = Objects.requireNonNull(expiration, "expiration");
        this.doitChangerMotDePasse = true;
    }

    public boolean jetonDefinitionExpire(Instant maintenant) {
        return jetonDefinitionExpiration == null || maintenant.isAfter(jetonDefinitionExpiration);
    }

    /** Compte cree au back-office dont le titulaire n a pas encore suivi son lien. */
    public boolean motDePasseADefinir() {
        return estAdministrateur() && statut == StatutUtilisateur.EN_ATTENTE_VALIDATION;
    }

    /**
     * Pose le premier mot de passe choisi par le titulaire et active le compte.
     *
     * <p>L adresse devient verifiee : le lien n est parvenu qu a elle, et le suivre est
     * la preuve qu elle est joignable, meme raisonnement qu a l activation d un membre.
     * Le jeton est efface, donc a usage unique.</p>
     */
    public void definirMotDePasse(String empreinte) {
        changerMotDePasse(empreinte);
        this.jetonDefinitionHache = null;
        this.jetonDefinitionExpiration = null;
        this.emailVerifie = true;
        if (this.statut == StatutUtilisateur.EN_ATTENTE_VALIDATION) {
            this.statut = StatutUtilisateur.ACTIF;
        }
    }

    // --- second facteur TOTP --------------------------------------------------------

    /** Obligatoire pour le back-office, facultatif pour un membre. */
    public boolean secondFacteurObligatoire() {
        return estAdministrateur();
    }

    public boolean secondFacteurActif() {
        return mfaActiveLe != null;
    }

    public boolean enrolementEnCours() {
        return mfaSecretChiffre != null && mfaActiveLe == null;
    }

    /**
     * Pose un secret en attente de confirmation. Refuse si un second facteur est deja
     * actif : le remplacer sans passer par la desactivation contournerait ses gardes.
     */
    public void commencerEnrolement(byte[] secretChiffre, short versionCle) {
        if (secondFacteurActif()) {
            throw new IllegalStateException("Un second facteur est deja actif.");
        }
        this.mfaSecretChiffre = Objects.requireNonNull(secretChiffre, "secretChiffre");
        this.mfaCleVersion = versionCle;
        this.mfaDernierPas = null;
    }

    /**
     * Active le secret en attente. {@code pas} est celui du code qui vient de le
     * confirmer : il est deja consomme et ne pourra pas servir une seconde fois.
     */
    public void confirmerEnrolement(Instant maintenant, long pas) {
        if (!enrolementEnCours()) {
            throw new IllegalStateException("Aucun enrolement en attente.");
        }
        this.mfaActiveLe = Objects.requireNonNull(maintenant, "maintenant");
        this.mfaDernierPas = pas;
    }

    /**
     * Anti-rejeu : un pas inferieur ou egal au dernier accepte est refuse ici aussi, et
     * pas seulement par le calcul qui l a trouve. L invariant appartient au compte.
     */
    public void accepterPas(long pas) {
        if (!secondFacteurActif()) {
            throw new IllegalStateException("Aucun second facteur actif.");
        }
        if (mfaDernierPas != null && pas <= mfaDernierPas) {
            throw new IllegalArgumentException("Pas TOTP deja consomme : " + pas);
        }
        this.mfaDernierPas = pas;
    }

    public void rechiffrerSecret(byte[] secretChiffre, short versionCle) {
        if (mfaSecretChiffre == null) {
            throw new IllegalStateException("Aucun secret a rechiffrer.");
        }
        this.mfaSecretChiffre = Objects.requireNonNull(secretChiffre, "secretChiffre");
        this.mfaCleVersion = versionCle;
    }

    /** Desactivation, reinitialisation par le super-administrateur, anonymisation. */
    public void retirerSecondFacteur() {
        this.mfaSecretChiffre = null;
        this.mfaCleVersion = null;
        this.mfaActiveLe = null;
        this.mfaDernierPas = null;
    }

    /**
     * Compte un echec du second facteur, code TOTP ou code de secours confondus.
     *
     * <p>Au seuil, le compte est verrouille pour une duree qui <b>double</b> a chaque
     * verrouillage, plafonnee. Cinq essais par quart d heure laisseraient environ 40 %
     * de chances sur un an a qui detient le mot de passe. Le doublement ramene le
     * budget a cinq essais par jour une fois le plafond atteint, soit moins de 1 %. Le
     * verrouillage reste temporaire, pour la raison deja donnee par
     * {@code TentativesConnexionService} : un blocage definitif se retournerait contre le
     * titulaire.</p>
     *
     * @return la fin du verrouillage s il vient d etre pose
     */
    public Optional<Instant> enregistrerEchecSecondFacteur(int seuil, Duration dureeInitiale,
                                                           Duration plafond, Instant maintenant) {
        this.mfaEchecs++;
        if (this.mfaEchecs < seuil) {
            return Optional.empty();
        }
        this.mfaEchecs = 0;
        this.mfaVerrouillages++;
        Duration duree = dureeInitiale;
        for (int i = 1; i < mfaVerrouillages && duree.compareTo(plafond) < 0; i++) {
            duree = duree.multipliedBy(2);
        }
        if (duree.compareTo(plafond) > 0) {
            duree = plafond;
        }
        this.verrouilleJusquA = maintenant.plus(duree);
        return Optional.of(this.verrouilleJusquA);
    }

    // --- accesseurs -----------------------------------------------------------------

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public TypeUtilisateur getTypeUtilisateur() { return typeUtilisateur; }
    public String getEmail() { return email; }
    public String getMotDePasseHache() { return motDePasseHache; }
    public String getNom() { return nom; }
    public String getPrenom() { return prenom; }
    public String getTelephone() { return telephone; }
    public String getRue() { return rue; }
    public String getNumeroRue() { return numeroRue; }
    public String getCodePostal() { return codePostal; }
    public String getLocalite() { return localite; }
    public String getPays() { return pays; }
    public Langue getLangue() { return langue; }
    public StatutUtilisateur getStatut() { return statut; }
    public boolean isEmailVerifie() { return emailVerifie; }
    public String getEmailEnAttente() { return emailEnAttente; }
    public String getJetonChangementEmail() { return jetonChangementEmail; }
    public Instant getJetonChangementExpiration() { return jetonChangementExpiration; }
    public String getJetonVerification() { return jetonVerification; }
    public Instant getJetonExpiration() { return jetonExpiration; }
    public Instant getDerniereConnexion() { return derniereConnexion; }
    public short getTentativesEchouees() { return tentativesEchouees; }
    public Instant getVerrouilleJusquA() { return verrouilleJusquA; }
    public String getFonction() { return fonction; }

    public void setTelephone(String telephone) { this.telephone = telephone; }
    public void setRue(String rue) { this.rue = rue; }
    public void setNumeroRue(String numeroRue) { this.numeroRue = numeroRue; }
    public void setCodePostal(String codePostal) { this.codePostal = codePostal; }
    public void setLocalite(String localite) { this.localite = localite; }
    public void setPays(String pays) { this.pays = pays; }
    public void setLangue(Langue langue) { this.langue = langue; }
    public void setStatut(StatutUtilisateur statut) { this.statut = statut; }
    public void setFonction(String fonction) { this.fonction = fonction; }
    public Instant getAnonymiseLe() { return anonymiseLe; }
    public byte[] getMfaSecretChiffre() { return mfaSecretChiffre; }
    public Short getMfaCleVersion() { return mfaCleVersion; }
    public Instant getMfaActiveLe() { return mfaActiveLe; }
    public Long getMfaDernierPas() { return mfaDernierPas; }
    public short getMfaEchecs() { return mfaEchecs; }
    public short getMfaVerrouillages() { return mfaVerrouillages; }
    public String getJetonDefinitionHache() { return jetonDefinitionHache; }
    public Instant getJetonDefinitionExpiration() { return jetonDefinitionExpiration; }


    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Utilisateur utilisateur)) return false;
        return id != null && id.equals(utilisateur.id);
    }

    @Override
    public int hashCode() {
        return Utilisateur.class.hashCode();
    }
}
