package be.autoservplus.identite.domain;

import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.context.MessageSource;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
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

    public void enregistrerConnexionReussie(Instant maintenant) {
        this.derniereConnexion = maintenant;
        this.tentativesEchouees = 0;
        this.verrouilleJusquA = null;
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

    public boolean estActif() {
        return statut == StatutUtilisateur.ACTIF;
    }

    public boolean estAdministrateur() {
        return typeUtilisateur == TypeUtilisateur.ADMINISTRATEUR;
    }

    public String nomComplet() {
        return prenom + " " + nom;
    }

    /**
     * Nom affichable dans la langue d un document.
     *
     * <p>Un compte anonymise rend le marqueur <b>traduit</b> ; un compte ordinaire rend
     * son vrai nom, qui ne se traduit pas. Utile partout ou le document a une langue
     * propre : une facture est emise dans celle du client, pas dans celle de la session
     * qui la telecharge, et le PDF regenere apres anonymisation doit suivre la meme
     * regle — un client neerlandophone ne doit pas recevoir « Client supprime ».</p>
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
     * NOT NULL, d ou un marqueur plutot que NULL — c est le meme tiret cadratin que les
     * gabarits emploient deja pour dire « rien ».
     */
    public static final String PAYS_ANONYME = "—";

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
     *         d un administrateur — F23 est un droit du membre sur son propre compte,
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
        this.derniereConnexion = null;
        this.tentativesEchouees = 0;
        this.verrouilleJusquA = null;
        this.fonction = null;
    }

    public boolean estAnonymise() {
        return anonymiseLe != null;
    }

    public void changerMotDePasse(String nouvelleEmpreinte) {
        this.motDePasseHache = Objects.requireNonNull(nouvelleEmpreinte, "nouvelleEmpreinte");
        this.tentativesEchouees = 0;
        this.verrouilleJusquA = null;
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
