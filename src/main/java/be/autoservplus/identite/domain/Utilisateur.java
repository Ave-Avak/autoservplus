package be.autoservplus.identite.domain;

import be.autoservplus.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
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

    public void changerMotDePasse(String nouvelleEmpreinte) {
        this.motDePasseHache = Objects.requireNonNull(nouvelleEmpreinte, "nouvelleEmpreinte");
        this.tentativesEchouees = 0;
        this.verrouilleJusquA = null;
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
