package be.autoservplus.identite.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Preuve horodatee de l acceptation d un document par un utilisateur.
 *
 * <p><b>Append-only</b> : une ligne s ecrit, ne se modifie jamais et ne se
 * supprime jamais — c est une preuve (pas de soft delete en base, aucun setter
 * ici). Un refus ou un retrait s exprime par une <i>nouvelle</i> ligne
 * {@code accorde = false}, jamais en retouchant celle-ci : voir la fabrique
 * {@link #decision}, employee par le consentement aux cookies (F25) ou l on
 * change d avis autant de fois qu on veut.</p>
 *
 * <p>Pour les CGV a la commande (F24), il s agit d une preuve <b>contractuelle</b>
 * — l acceptation des conditions exigee avant la vente (execution du contrat,
 * art. 6.1.b RGPD) — pas d un consentement optionnel : elle conditionne la
 * commande, elle ne se « refuse » pas apres coup. L adresse IP est une donnee
 * personnelle conservee ici pour ce seul usage de preuve : elle ne transite ni
 * par les URL ni par les journaux.</p>
 */
@Entity
@Table(name = "consentement")
@EntityListeners(AuditingEntityListener.class)
public class Consentement {

    /**
     * Version des CGV en vigueur, figee sur chaque preuve d acceptation.
     * Raccourci V1 assume : le versionnage complet des documents (numero, date
     * d entree en vigueur, re-acceptation a chaque modification — F24) est une
     * evolution ; d ici la, cette constante est l unique source de la version.
     */
    public static final String CGV_VERSION_COURANTE = "CGV-2026-01";

    /**
     * Version de la politique cookies en vigueur, figee sur chaque preuve de choix
     * (F25).
     *
     * <p><b>F24 n est pas ici une dette de confort : c est un prerequis de
     * conformite.</b> Un consentement ne vaut que pour les finalites effectivement
     * presentees au moment ou il a ete donne. Le jour ou une finalite sera ajoutee ou
     * elargie, les choix recueillis sur l ancienne liste cesseront de couvrir la
     * nouvelle, et il faudra <b>reposer la question</b> — sans quoi un traceur serait
     * charge sur la foi d un accord qui ne le visait pas, c est-a-dire sans
     * consentement. Le declencheur de cette redemande est le changement de version,
     * donc precisement ce que cette constante devra cesser d etre.</p>
     *
     * <p>En V1 le risque est nul et le raccourci tenable, parce qu <b>aucun traceur
     * optionnel n est installe</b> : rien ne se charge, quel que soit le choix. La
     * contrainte devient bloquante au moment ou un traceur reel sera cable — le
     * versionnage des documents doit alors etre livre <b>avant</b>, pas apres.</p>
     */
    public static final String COOKIES_VERSION_COURANTE = "COOKIES-2026-01";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false, updatable = false)
    private Utilisateur utilisateur;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false, length = 30, updatable = false)
    private TypeDocumentConsentement typeDocument;

    @NotNull
    @Column(name = "version_acceptee", nullable = false, length = 20, updatable = false)
    private String versionAcceptee;

    @Column(name = "accorde", nullable = false, updatable = false)
    private boolean accorde;

    @Column(name = "date_consentement", nullable = false, updatable = false)
    private Instant dateConsentement;

    @Column(name = "adresse_ip", length = 45, updatable = false)
    private String adresseIp;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 120, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    protected Consentement() {
        // requis par JPA
    }

    private Consentement(Utilisateur utilisateur, TypeDocumentConsentement typeDocument,
                         String versionAcceptee, boolean accorde, String adresseIp,
                         Instant dateConsentement) {
        this.utilisateur = Objects.requireNonNull(utilisateur, "utilisateur");
        this.typeDocument = Objects.requireNonNull(typeDocument, "typeDocument");
        this.versionAcceptee = Objects.requireNonNull(versionAcceptee, "versionAcceptee");
        this.accorde = accorde;
        this.adresseIp = adresseIp;
        this.dateConsentement = Objects.requireNonNull(dateConsentement, "dateConsentement");
    }

    /**
     * Preuve d acceptation d un document. L instant vient de l horloge injectee de
     * l appelant, jamais de {@code Instant.now()} ; l adresse IP peut etre absente
     * (colonne nullable) si la requete ne permet pas de la determiner.
     */
    public static Consentement acceptation(Utilisateur utilisateur,
                                           TypeDocumentConsentement typeDocument,
                                           String versionAcceptee,
                                           String adresseIp,
                                           Instant dateConsentement) {
        return decision(utilisateur, typeDocument, versionAcceptee, true,
                adresseIp, dateConsentement);
    }

    /**
     * Preuve d un choix, accorde ou refuse (F25).
     *
     * <p>Distincte de {@link #acceptation} parce que les deux issues sont ici
     * legitimes et doivent toutes deux se prouver : demontrer qu un consentement a
     * ete recueilli suppose de pouvoir montrer aussi bien le oui que le non, faute
     * de quoi l absence de ligne resterait ambigue — refus explicite ou visiteur
     * jamais interroge ? Un refus s enregistre donc, il ne s omet pas.</p>
     */
    public static Consentement decision(Utilisateur utilisateur,
                                        TypeDocumentConsentement typeDocument,
                                        String versionAcceptee,
                                        boolean accorde,
                                        String adresseIp,
                                        Instant dateConsentement) {
        return new Consentement(utilisateur, typeDocument, versionAcceptee, accorde,
                adresseIp, dateConsentement);
    }

    public Long getId() { return id; }
    public Utilisateur getUtilisateur() { return utilisateur; }
    public TypeDocumentConsentement getTypeDocument() { return typeDocument; }
    public String getVersionAcceptee() { return versionAcceptee; }
    public boolean isAccorde() { return accorde; }
    public Instant getDateConsentement() { return dateConsentement; }
    public String getAdresseIp() { return adresseIp; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Consentement consentement)) return false;
        return id != null && id.equals(consentement.id);
    }

    @Override
    public int hashCode() {
        return Consentement.class.hashCode();
    }
}
