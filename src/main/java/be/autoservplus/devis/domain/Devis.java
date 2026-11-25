package be.autoservplus.devis.domain;

import be.autoservplus.common.montant.CalculTva;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Devis émis par le membre depuis son panier (F15).
 *
 * <p>Instantané du panier au moment de la demande : lignes, prix et taux, client et
 * véhicule nommés. Le panier change ensuite, le devis non. Il ne s'accepte pas et ne
 * devient pas une commande en V1 : son seul usage est d'être présenté, et il doit
 * pouvoir être reproduit à l'identique, d'où les colonnes recopiées plutôt qu'un
 * renvoi vers le panier ou le compte.</p>
 *
 * <p>Les prix sont ceux des lignes du panier, figés à l'ajout : ce sont ceux qu'une
 * commande passée le même jour appliquerait. Les totaux suivent le même calcul que la
 * commande ({@link CalculTva}).</p>
 */
@Entity
@Table(name = "devis")
@EntityListeners(AuditingEntityListener.class)
public class Devis {

    /** Durée de validité annoncée par le document, comptée en jours depuis l'émission. */
    public static final int JOURS_DE_VALIDITE = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, updatable = false)
    private UUID reference;

    @Column(name = "numero", nullable = false, updatable = false, length = 20)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membre_id", nullable = false, updatable = false)
    private Utilisateur membre;

    @Column(name = "date_emission", nullable = false, updatable = false)
    private Instant dateEmission;

    @Column(name = "valable_jusqu_au", nullable = false, updatable = false)
    private LocalDate valableJusquAu;

    @Enumerated(EnumType.STRING)
    @Column(name = "langue_document", nullable = false, updatable = false, length = 2)
    private Langue langueDocument;

    @Column(name = "client_prenom", nullable = false, updatable = false, length = 80)
    private String clientPrenom;

    @Column(name = "client_nom", nullable = false, updatable = false, length = 80)
    private String clientNom;

    @Column(name = "client_rue", updatable = false, length = 150)
    private String clientRue;

    @Column(name = "client_numero_rue", updatable = false, length = 15)
    private String clientNumeroRue;

    @Column(name = "client_code_postal", updatable = false, length = 10)
    private String clientCodePostal;

    @Column(name = "client_localite", updatable = false, length = 100)
    private String clientLocalite;

    @Column(name = "client_pays", updatable = false, length = 60)
    private String clientPays;

    @Column(name = "vehicule_plaque", nullable = false, updatable = false, length = 15)
    private String vehiculePlaque;

    @Column(name = "vehicule_marque", nullable = false, updatable = false, length = 60)
    private String vehiculeMarque;

    @Column(name = "vehicule_modele", nullable = false, updatable = false, length = 80)
    private String vehiculeModele;

    @Column(name = "montant_htva", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal montantHtva;

    @Column(name = "montant_tva", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal montantTva;

    @Column(name = "montant_tvac", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal montantTvac;

    @OneToMany(mappedBy = "devis", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("rang")
    private List<LigneDevis> lignes = new ArrayList<>();

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

    protected Devis() {
        // requis par JPA
    }

    /**
     * Devis du panier, pour ce véhicule.
     *
     * <p>Les libellés sont pris dans la langue du document, avec repli sur le français
     * pour un article non traduit, comme ceux d'une commande à sa conclusion. Le
     * véhicule doit appartenir au titulaire du panier : un devis ne nomme jamais le
     * véhicule d'un autre membre.</p>
     *
     * @param jourEmission jour de l'émission au fuseau du garage, d'où se compte la validité
     * @throws IllegalArgumentException si le panier est vide ou si le véhicule est d'un autre membre
     */
    public static Devis depuisLePanier(String numero, Panier panier, Vehicule vehicule,
                                       Instant emission, LocalDate jourEmission, Langue langue) {
        Objects.requireNonNull(panier, "panier");
        Objects.requireNonNull(vehicule, "vehicule");
        if (panier.estVide()) {
            throw new IllegalArgumentException("Un devis ne s'émet pas sur un panier vide.");
        }
        if (!vehicule.appartientA(panier.getMembre())) {
            throw new IllegalArgumentException("Le véhicule n'appartient pas au titulaire du panier.");
        }
        Devis devis = new Devis();
        devis.reference = UUID.randomUUID();
        devis.numero = Objects.requireNonNull(numero, "numero");
        devis.membre = panier.getMembre();
        devis.dateEmission = Objects.requireNonNull(emission, "emission");
        devis.valableJusquAu = jourEmission.plusDays(JOURS_DE_VALIDITE);
        devis.langueDocument = Objects.requireNonNull(langue, "langue");

        Utilisateur client = panier.getMembre();
        devis.clientPrenom = client.getPrenom();
        devis.clientNom = client.getNom();
        devis.clientRue = client.getRue();
        devis.clientNumeroRue = client.getNumeroRue();
        devis.clientCodePostal = client.getCodePostal();
        devis.clientLocalite = client.getLocalite();
        devis.clientPays = client.getPays();

        devis.vehiculePlaque = vehicule.getPlaque();
        devis.vehiculeMarque = vehicule.getMarque();
        devis.vehiculeModele = vehicule.getModele();

        Locale langueDuDocument = Locale.forLanguageTag(langue.name());
        short rang = 0;
        for (LignePanier ligne : panier.getLignes()) {
            devis.lignes.add(new LigneDevis(devis, rang++, ligne.libelleAffiche(langueDuDocument),
                    ligne.getQuantite(), ligne.getPrixUnitaireHtva(), ligne.getTauxTva()));
        }
        devis.montantHtva = panier.totalHtva();
        devis.montantTva = panier.totalTva();
        devis.montantTvac = panier.totalTvac();
        return devis;
    }

    /** Base et TVA par taux, recalculées sur les lignes comme le panier les a calculées. */
    public List<CalculTva.Tranche> ventilation() {
        return CalculTva.ventiler(lignes.stream()
                .map(ligne -> new CalculTva.MontantTaxe(ligne.getTauxTva(), ligne.totalTvac()))
                .toList());
    }

    /** Le devis n'est plus valable le lendemain de son dernier jour de validité. */
    public boolean estExpireLe(LocalDate jour) {
        return jour.isAfter(valableJusquAu);
    }

    public String vehiculeDesignation() {
        return "%s %s (%s)".formatted(vehiculeMarque, vehiculeModele, vehiculePlaque);
    }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public String getNumero() { return numero; }
    public Utilisateur getMembre() { return membre; }
    public Instant getDateEmission() { return dateEmission; }
    public LocalDate getValableJusquAu() { return valableJusquAu; }
    public Langue getLangueDocument() { return langueDocument; }
    public String getClientPrenom() { return clientPrenom; }
    public String getClientNom() { return clientNom; }
    public String getClientRue() { return clientRue; }
    public String getClientNumeroRue() { return clientNumeroRue; }
    public String getClientCodePostal() { return clientCodePostal; }
    public String getClientLocalite() { return clientLocalite; }
    public String getClientPays() { return clientPays; }
    public String getVehiculePlaque() { return vehiculePlaque; }
    public String getVehiculeMarque() { return vehiculeMarque; }
    public String getVehiculeModele() { return vehiculeModele; }
    public BigDecimal getMontantHtva() { return montantHtva; }
    public BigDecimal getMontantTva() { return montantTva; }
    public BigDecimal getMontantTvac() { return montantTvac; }
    public List<LigneDevis> getLignes() { return Collections.unmodifiableList(lignes); }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof Devis devis)) return false;
        return id != null && id.equals(devis.id);
    }

    @Override
    public int hashCode() {
        return Devis.class.hashCode();
    }
}
