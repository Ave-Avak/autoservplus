package be.autoservplus.rgpd.service.dto;

import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.reservation.domain.LigneRdv;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Contenu de l export du droit d acces (F22, article 15 RGPD), destine a etre
 * serialise en JSON.
 *
 * <p>Objet de transfert pur : aucune entite JPA ne sort d ici. C est une exigence
 * de conception, pas de style — serialiser les entites exposerait les colonnes
 * techniques (empreinte du mot de passe, jetons), declencherait des relations
 * paresseuses hors session, et rendrait le perimetre de l export dependant du
 * mapping plutot que d une decision explicite. Ici, ce qui sort est ce qui est
 * ecrit ci-dessous, et rien d autre.
 *
 * <p>Trois blocs :
 * <ul>
 *   <li>{@code donneesPersonnelles} : les donnees du membre, lues en base ;</li>
 *   <li>{@code informationsTraitement} : le rappel legal exige par l article 15,
 *       qui decrit le traitement et ne vient pas du dossier du membre ;</li>
 *   <li>{@code exclusions} : ce qui n est volontairement pas communique, et
 *       pourquoi. Le silence serait ambigu — une absence peut se lire comme un
 *       oubli. La note sur la carte bancaire, notamment, affirme une absence de
 *       collecte que le membre ne peut pas deduire du reste du fichier.</li>
 * </ul>
 *
 * <p>Les montants restent des {@link BigDecimal} et les instants des
 * {@link Instant} : le fichier sert aussi la portabilite (article 20), il doit
 * rester relisible par machine. Le formatage belge de {@code FormatageRdv} est
 * reserve aux ecrans.
 */
public record ExportDonnees(
        Instant genereLe,
        DonneesPersonnelles donneesPersonnelles,
        InformationsTraitement informationsTraitement,
        Exclusions exclusions) {

    /** Les donnees du membre effectivement detenues en base. */
    public record DonneesPersonnelles(
            ProfilExport profil,
            List<VehiculeExport> vehicules,
            List<CommandeExport> commandes,
            List<RdvExport> rendezVous,
            List<InterventionExport> interventions,
            List<ConsentementExport> consentements,
            ConnexionExport connexionEtSecurite) {
    }

    /**
     * Profil du membre.
     *
     * <p>L adresse est restituee en sous-champs parce que le schema reel la stocke
     * ainsi ({@code rue}, {@code numero_rue}, {@code code_postal}, {@code localite},
     * {@code pays} des V1) : l export suit la base, pas le livrable.
     *
     * <p>{@code consentementMarketing} n est pas une colonne : il se <b>deduit</b>
     * de la derniere preuve de type NEWSLETTER de la table {@code consentement}.
     * Une seule source de verite pour le consentement, et l historique complet
     * reste visible dans la section {@code consentements}.
     */
    public record ProfilExport(
            String nom,
            String prenom,
            String email,
            String telephone,
            AdresseExport adresse,
            String langue,
            boolean consentementMarketing,
            String statutCompte,
            Instant dateCreationCompte) {

        public static ProfilExport de(Utilisateur membre, List<Consentement> consentements) {
            return new ProfilExport(
                    membre.getNom(),
                    membre.getPrenom(),
                    membre.getEmail(),
                    membre.getTelephone(),
                    AdresseExport.de(membre),
                    membre.getLangue().name(),
                    marketingAccorde(consentements),
                    membre.getStatut().name(),
                    membre.getCreatedAt());
        }

        /**
         * Etat courant du consentement marketing : la <b>derniere</b> preuve
         * NEWSLETTER fait foi. La table est append-only, un retrait s y exprime par
         * une nouvelle ligne {@code accorde = false} — lire la premiere ligne, ou
         * n importe quelle ligne accordee, restituerait un consentement retire.
         */
        private static boolean marketingAccorde(List<Consentement> consentements) {
            return consentements.stream()
                    .filter(c -> c.getTypeDocument() == TypeDocumentConsentement.NEWSLETTER)
                    .reduce((premier, suivant) -> suivant)
                    .map(Consentement::isAccorde)
                    .orElse(false);
        }
    }

    public record AdresseExport(
            String rue,
            String numero,
            String codePostal,
            String localite,
            String pays) {

        public static AdresseExport de(Utilisateur membre) {
            return new AdresseExport(membre.getRue(), membre.getNumeroRue(),
                    membre.getCodePostal(), membre.getLocalite(), membre.getPays());
        }
    }

    /**
     * Vehicule du membre, champs metier uniquement : les colonnes d audit
     * ({@code updated_by}, versions) decrivent l exploitation de la plateforme, pas
     * la personne. {@code dateAjout} fait exception — savoir depuis quand une donnee
     * est detenue releve de l information du membre.
     */
    public record VehiculeExport(
            String plaque,
            String marque,
            String modele,
            String motorisation,
            Short annee,
            Integer kilometrage,
            String numeroChassis,
            boolean actif,
            Instant dateAjout) {

        public static VehiculeExport de(Vehicule vehicule) {
            return new VehiculeExport(
                    vehicule.getPlaque(),
                    vehicule.getMarque(),
                    vehicule.getModele(),
                    vehicule.getMotorisation().name(),
                    vehicule.getAnnee(),
                    vehicule.getKilometrage(),
                    vehicule.getNumeroChassis(),
                    vehicule.isActif(),
                    vehicule.getCreatedAt());
        }
    }

    /** Commande et ses lignes, aux valeurs figees a l ajout au panier (RM-30). */
    public record CommandeExport(
            String numero,
            String statut,
            Instant dateCommande,
            Instant datePaiement,
            Instant dateAnnulation,
            String motifAnnulation,
            BigDecimal montantHtva,
            BigDecimal montantTva,
            BigDecimal montantTvac,
            List<LigneCommandeExport> lignes) {

        public static CommandeExport de(Commande commande, List<LignePanier> lignes) {
            return new CommandeExport(
                    commande.getNumero(),
                    commande.getStatut().name(),
                    commande.getDateCommande(),
                    commande.getDatePaiement(),
                    commande.getDateAnnulation(),
                    commande.getMotifAnnulation() == null
                            ? null : commande.getMotifAnnulation().name(),
                    commande.getMontantHtva(),
                    commande.getMontantTva(),
                    commande.getMontantTvac(),
                    lignes.stream().map(LigneCommandeExport::de).toList());
        }
    }

    public record LigneCommandeExport(
            String libelle,
            short quantite,
            BigDecimal prixUnitaireHtva,
            BigDecimal tauxTva,
            BigDecimal totalHtva,
            BigDecimal totalTva,
            BigDecimal totalTvac) {

        public static LigneCommandeExport de(LignePanier ligne) {
            return new LigneCommandeExport(
                    ligne.getLibelleFige(),
                    ligne.getQuantite(),
                    ligne.getPrixUnitaireHtva(),
                    ligne.getTauxTva(),
                    ligne.totalHtva(),
                    ligne.totalTva(),
                    ligne.totalTvac());
        }
    }

    /**
     * Rendez-vous du membre. Le poste d atelier affecte n est pas restitue : c est
     * une ressource d organisation du garage, elle ne decrit pas la personne.
     */
    public record RdvExport(
            String numero,
            String statut,
            Instant debut,
            Instant fin,
            String vehicule,
            String commentaire,
            String motifRefus,
            Instant dateAnnulation,
            BigDecimal montantHtva,
            BigDecimal montantTvac,
            List<PrestationRdvExport> prestations) {

        public static RdvExport de(Rdv rdv) {
            return new RdvExport(
                    rdv.getNumero(),
                    rdv.getStatut().name(),
                    rdv.getDebut(),
                    rdv.getFin(),
                    rdv.getVehicule().getPlaque(),
                    rdv.getCommentaire(),
                    rdv.getMotifRefus(),
                    rdv.getDateAnnulation(),
                    rdv.montantHtva(),
                    rdv.montantTvac(),
                    rdv.getLignes().stream().map(PrestationRdvExport::de).toList());
        }
    }

    public record PrestationRdvExport(
            String libelle,
            short quantite,
            BigDecimal prixUnitaireHtva,
            BigDecimal tauxTva) {

        public static PrestationRdvExport de(LigneRdv ligne) {
            return new PrestationRdvExport(
                    ligne.getPrestation().getLibelle(),
                    ligne.getQuantite(),
                    ligne.getPrixUnitaireHtva(),
                    ligne.getTauxTva());
        }
    }

    /**
     * Intervention sur un vehicule du membre.
     *
     * <p><b>RM-16 etendue a l export</b> : le statut restitue est le statut
     * <i>percu</i>, celui qui est communique au membre sur son ecran de suivi, et
     * non le statut technique. SUSPENDUE et ATTENTE_VALIDATION_MEMBRE decrivent
     * l organisation interne de l atelier ; la personne, elle, est informee que son
     * intervention est en cours. Restituer ici les sous-etats techniques
     * introduirait une seconde verite sur la meme donnee. Arbitrage a documenter au
     * rapport ecrit (article 15 contre RM-16).
     *
     * <p>Le commentaire restitue est {@code commentaireAdmin}, deja visible du
     * membre dans son suivi ; il n existe pas de diagnostic technique separe en V1.
     */
    public record InterventionExport(
            String numero,
            String statut,
            String rendezVous,
            String vehicule,
            Instant debutReel,
            Instant finReelle,
            String commentaireGarage,
            BigDecimal montantDevisInitialHtva,
            BigDecimal totalFacturableHtva,
            BigDecimal totalFacturableTvac,
            List<LigneInterventionExport> lignes) {

        public static InterventionExport de(Intervention intervention) {
            return new InterventionExport(
                    intervention.getNumero(),
                    intervention.getStatut().percuLabel(),
                    intervention.getRdv() == null ? null : intervention.getRdv().getNumero(),
                    intervention.getVehicule().getPlaque(),
                    intervention.getDebutReel(),
                    intervention.getFinReelle(),
                    intervention.getCommentaireAdmin(),
                    intervention.getMontantDevisInitialHtva(),
                    intervention.totalFacturableHtva(),
                    intervention.totalFacturableTvac(),
                    intervention.getLignes().stream().map(LigneInterventionExport::de).toList());
        }
    }

    /**
     * Ligne d intervention. {@code accordMembre} est restitue tel quel, y compris
     * {@code null} : c est la reponse du membre a une demande de depassement de
     * devis (RM-15) — sa decision, donc sa donnee. {@code null} signifie qu aucune
     * reponse n est attendue ni donnee.
     */
    public record LigneInterventionExport(
            String type,
            String libelle,
            short quantite,
            BigDecimal prixUnitaireHtva,
            BigDecimal tauxTva,
            BigDecimal totalHtva,
            BigDecimal totalTvac,
            Boolean accordMembre) {

        public static LigneInterventionExport de(LigneIntervention ligne) {
            return new LigneInterventionExport(
                    ligne.getType().name(),
                    ligne.getLibelleFige(),
                    ligne.getQuantite(),
                    ligne.getPrixUnitaireHtva(),
                    ligne.getTauxTva(),
                    ligne.totalHtva(),
                    ligne.totalTvac(),
                    ligne.getAccordMembre());
        }
    }

    /**
     * Preuve de consentement. L <b>adresse IP</b> et l horodatage sont restitues :
     * ce sont des donnees a caractere personnel collectees a l occasion du
     * consentement, elles entrent dans le champ de l article 15 au meme titre que
     * le reste. Les omettre parce qu elles servent de preuve au responsable du
     * traitement reviendrait a soustraire a la personne une donnee prise sur elle.
     */
    public record ConsentementExport(
            String typeDocument,
            String versionAcceptee,
            boolean accorde,
            Instant dateConsentement,
            String adresseIp) {

        public static ConsentementExport de(Consentement consentement) {
            return new ConsentementExport(
                    consentement.getTypeDocument().name(),
                    consentement.getVersionAcceptee(),
                    consentement.isAccorde(),
                    consentement.getDateConsentement(),
                    consentement.getAdresseIp());
        }
    }

    /**
     * Donnees de connexion reellement conservees par la V1. Il n existe pas de
     * journal des tentatives : la table {@code utilisateur} ne garde qu un
     * <b>compteur</b> courant, remis a zero a chaque connexion reussie. L export
     * dit donc ce qui existe, sans laisser croire a un historique.
     *
     * <p>Sont exclus le jeton de verification et sa date d expiration : ce sont des
     * secrets d authentification, communicables a personne — voir {@link Exclusions}.
     */
    public record ConnexionExport(
            Instant derniereConnexion,
            boolean emailVerifie,
            short tentativesEchoueesEnCours,
            Instant compteVerrouilleJusquA) {

        public static ConnexionExport de(Utilisateur membre) {
            return new ConnexionExport(
                    membre.getDerniereConnexion(),
                    membre.isEmailVerifie(),
                    membre.getTentativesEchouees(),
                    membre.getVerrouilleJusquA());
        }
    }

    /** Ce qui ne figure pas dans l export, et la raison de chaque omission. */
    public record Exclusions(
            String motDePasse,
            String donneesBancaires,
            String secretsTechniques) {
    }
}
