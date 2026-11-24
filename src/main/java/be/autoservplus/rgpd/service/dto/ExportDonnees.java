package be.autoservplus.rgpd.service.dto;

import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.EvenementSecuriteCompte;
import be.autoservplus.identite.domain.ModificationCompte;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.reservation.domain.LigneRdv;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Contenu de l'export du droit d'accès (F22, article 15 RGPD), destiné à être
 * sérialisé en JSON.
 *
 * <p>Objet de transfert pur : aucune entité JPA ne sort d'ici. C'est une exigence
 * de conception, pas de style : sérialiser les entités exposerait les colonnes
 * techniques (empreinte du mot de passe, jetons), déclencherait des relations
 * paresseuses hors session, et rendrait le périmètre de l'export dépendant du
 * mapping plutôt que d'une décision explicite. Ici, ce qui sort est ce qui est
 * écrit ci-dessous, et rien d'autre.
 *
 * <p>Trois blocs :
 * <ul>
 *   <li>{@code donneesPersonnelles} : les données du membre, lues en base.</li>
 *   <li>{@code informationsTraitement} : le rappel légal exigé par l'article 15,
 *       qui décrit le traitement et ne vient pas du dossier du membre.</li>
 *   <li>{@code exclusions} : ce qui n'est volontairement pas communiqué, et
 *       pourquoi. Le silence serait ambigu : une absence peut se lire comme un
 *       oubli. La note sur la carte bancaire, notamment, affirme une absence de
 *       collecte que le membre ne peut pas déduire du reste du fichier.</li>
 * </ul>
 *
 * <p>Les montants restent des {@link BigDecimal} et les instants des
 * {@link Instant} : le fichier sert aussi la portabilité (article 20), il doit
 * rester relisible par machine. Le formatage belge de {@code FormatageRdv} est
 * réservé aux écrans.
 */
public record ExportDonnees(
        Instant genereLe,
        DonneesPersonnelles donneesPersonnelles,
        InformationsTraitement informationsTraitement,
        Exclusions exclusions) {

    /** Les données du membre effectivement détenues en base. */
    public record DonneesPersonnelles(
            ProfilExport profil,
            List<VehiculeExport> vehicules,
            PanierExport panierEnCours,
            List<CommandeExport> commandes,
            List<FactureExport> factures,
            List<RdvExport> rendezVous,
            List<InterventionExport> interventions,
            List<ConsentementExport> consentements,
            ConnexionExport connexionEtSecurite,
            List<ModificationCompteExport> modificationsDuCompte) {
    }

    /**
     * Profil du membre.
     *
     * <p>L'adresse est restituée en sous-champs parce que le schéma réel la stocke
     * ainsi ({@code rue}, {@code numero_rue}, {@code code_postal}, {@code localite},
     * {@code pays} dès V1) : l'export suit la base, pas le livrable.
     *
     * <p>{@code consentementMarketing} n'est pas une colonne : il se <b>déduit</b>
     * de la dernière preuve de type NEWSLETTER de la table {@code consentement}.
     * Une seule source de vérité pour le consentement, et l'historique complet
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
         * État courant du consentement marketing : la <b>dernière</b> preuve
         * NEWSLETTER fait foi. La table est append-only, un retrait s'y exprime par
         * une nouvelle ligne {@code accorde = false} : lire la première ligne, ou
         * n'importe quelle ligne accordée, restituerait un consentement retiré.
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
     * Véhicule du membre, champs métier uniquement : les colonnes d'audit
     * ({@code updated_by}, versions) décrivent l'exploitation de la plateforme, pas
     * la personne. {@code dateAjout} fait exception : savoir depuis quand une donnée
     * est détenue relève de l'information du membre.
     *
     * <p><b>Deux états distincts</b>, souvent confondus :
     * <ul>
     *   <li>{@code actif} : le véhicule ne circule plus, le membre ne le propose
     *       plus à la réservation. Drapeau métier.</li>
     *   <li>{@code supprime} : le membre a retiré le véhicule de son parc. La ligne
     *       reste en base (l'effacer reviendrait à effacer des factures déjà
     *       émises) donc la donnée est toujours <b>détenue</b> et l'article 15
     *       impose de la restituer. Elle est marquée comme telle, avec sa date :
     *       la sortir sans marqueur laisserait croire au membre qu'il possède
     *       encore ce véhicule.</li>
     * </ul>
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
            boolean supprime,
            Instant dateSuppression,
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
                    vehicule.estSupprime(),
                    vehicule.getDeletedAt(),
                    vehicule.getCreatedAt());
        }
    }

    /**
     * Panier en cours du membre : des articles choisis, jamais commandés, mais
     * <b>détenus</b>, car l'article 15 ne s'arrête pas aux données qui ont abouti à une
     * transaction. C'est aussi la seule section de l'export qui puisse encore
     * changer d'une minute à l'autre, d'où l'horodatage du document en tête de
     * fichier.
     *
     * <p>Un membre sans panier obtient une section vide plutôt que rien : la
     * plateforme affirme ainsi ne rien détenir, au lieu de laisser une absence
     * ambiguë. Même raisonnement que les notes d'{@link Exclusions}.
     *
     * <p>Les totaux viennent de l'entité ({@code RM-30} : somme des montants de
     * ligne déjà arrondis, jamais un calcul sur un total global). Les recalculer
     * ici créerait une seconde implémentation d'une règle délicate.
     */
    public record PanierExport(
            Instant dateCreation,
            int nombreArticles,
            BigDecimal totalHtva,
            BigDecimal totalTva,
            BigDecimal totalTvac,
            List<LignePanierExport> lignes) {

        public static PanierExport de(Panier panier, Map<Long, Instant> datesAjout) {
            return new PanierExport(
                    panier.getCreatedAt(),
                    panier.nombreArticles(),
                    panier.totalHtva(),
                    panier.totalTva(),
                    panier.totalTvac(),
                    panier.getLignes().stream()
                            .map(ligne -> LignePanierExport.de(ligne,
                                    datesAjout.get(ligne.getId())))
                            .toList());
        }

        /** Aucun panier en cours : la section existe, elle est vide. */
        public static PanierExport vide() {
            BigDecimal zero = new BigDecimal("0.00");
            return new PanierExport(null, 0, zero, zero, zero, List.of());
        }
    }

    /**
     * Ligne du panier, aux conditions figées à l'ajout (RM-30).
     *
     * <p>{@code dateAjout} vient d'une requête distincte : la colonne
     * {@code ligne_panier.created_at} existe et est mappée, mais l'entité n'en
     * expose aucun accesseur, et l'ajouter modifierait le module {@code vente}.
     * Savoir depuis quand une donnée est détenue relève pourtant de l'information
     * de la personne, la valeur est donc lue par le chemin qui ne touche à rien.
     */
    public record LignePanierExport(
            String libelle,
            short quantite,
            BigDecimal prixUnitaireHtva,
            BigDecimal tauxTva,
            BigDecimal prixUnitaireTvac,
            BigDecimal totalTvac,
            Instant dateAjout) {

        public static LignePanierExport de(LignePanier ligne, Instant dateAjout) {
            return new LignePanierExport(
                    ligne.getLibelleFige(),
                    ligne.getQuantite(),
                    ligne.getPrixUnitaireHtva(),
                    ligne.getTauxTva(),
                    ligne.prixUnitaireTvac(),
                    ligne.totalTvac(),
                    dateAjout);
        }
    }

    /** Commande et ses lignes, aux valeurs figées à l'ajout au panier (RM-30). */
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
            BigDecimal prixUnitaireTvac,
            BigDecimal totalTvac) {

        public static LigneCommandeExport de(LignePanier ligne) {
            return new LigneCommandeExport(
                    ligne.getLibelleFige(),
                    ligne.getQuantite(),
                    ligne.getPrixUnitaireHtva(),
                    ligne.getTauxTva(),
                    ligne.prixUnitaireTvac(),
                    ligne.totalTvac());
        }
    }

    /**
     * Facture émise au nom du membre (F31).
     *
     * <p>Section distincte des commandes, et non un doublon : une commande est un
     * acte d'achat, une facture est un document <b>comptable</b> qui l'atteste,
     * porte son propre numéro légal et sa propre date d'émission. Une commande
     * annulée n'a pas de facture. Une facture, elle, ne disparaît jamais. Les
     * deux sont détenues, l'article 15 vise les deux.
     *
     * <p>{@code numero} est le numéro légal, d'une suite continue par exercice
     * (AR n°1, art. 5) : c'est sous ce numéro que le document existe pour
     * l'administration, et c'est lui que la personne doit pouvoir citer.
     *
     * <p>Les montants sont ceux <b>figés à l'émission</b>, recopiés de la commande.
     * Ils peuvent différer de ceux du catalogue courant, et c'est le propos : la
     * facture atteste ce qui a été encaissé, pas ce que l'article coûte aujourd'hui.
     *
     * <p>{@code numeroCommande} rattache la facture à la section commandes du même
     * fichier : le numéro fonctionnel plutôt que la référence technique, pour que
     * la personne puisse faire le rapprochement elle-même. {@code null} pour une
     * facture d'intervention (RM-17, bloc futur).
     *
     * <p><b>Le PDF n'est pas inclus.</b> L'export est un document de données, pas
     * une archive de fichiers : y embarquer des binaires encodés le rendrait
     * illisible et ferait doublon avec un téléchargement déjà offert au membre,
     * facture par facture. {@code pdfArchive} dit seulement si un fichier PDF est
     * <b>actuellement conservé</b> pour cette facture : une donnée sur ce qui est
     * détenu. Il vaut {@code false} tant que personne ne l'a demandé, le document
     * étant fabriqué à la première demande. Il reste téléchargeable dans tous les
     * cas depuis « Mes commandes ».
     */
    public record FactureExport(
            String numero,
            Instant dateEmission,
            BigDecimal montantHtva,
            BigDecimal montantTva,
            BigDecimal montantTvac,
            String numeroCommande,
            boolean pdfArchive,
            String clientPrenom,
            String clientNom,
            AdresseExport adresseClient) {

        /**
         * L'instantané du client (V61) est restitué tel que la facture le porte : c'est
         * une donnée sur la personne, conservée dix ans même après la suppression du
         * compte, et que le membre doit pouvoir lire.
         */
        public static FactureExport de(Facture facture) {
            return new FactureExport(
                    facture.getNumero(),
                    facture.getDateEmission(),
                    facture.getMontantHtva(),
                    facture.getMontantTva(),
                    facture.getMontantTvac(),
                    facture.getCommande() == null ? null : facture.getCommande().getNumero(),
                    facture.estArchivee(),
                    facture.getClientPrenom(),
                    facture.getClientNom(),
                    new AdresseExport(facture.getClientRue(), facture.getClientNumeroRue(),
                            facture.getClientCodePostal(), facture.getClientLocalite(),
                            facture.getClientPays()));
        }
    }

    /**
     * Rendez-vous du membre. Le poste d'atelier affecté n'est pas restitué : c'est
     * une ressource d'organisation du garage, elle ne décrit pas la personne.
     *
     * <p>La plaque arrive en argument plutôt que d'être lue sur la relation : le
     * véhicule porte un {@code @SQLRestriction}, et le rendez-vous d'un véhicule
     * retiré du parc doit rester dans l'export. Le service la rapproche depuis la
     * liste complète des véhicules, suppressions logiques comprises.
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

        public static RdvExport de(Rdv rdv, String plaque) {
            return new RdvExport(
                    rdv.getNumero(),
                    rdv.getStatut().name(),
                    rdv.getDebut(),
                    rdv.getFin(),
                    plaque,
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
     * Intervention sur un véhicule du membre.
     *
     * <p><b>RM-16 étendue à l'export</b> : le statut restitué est le statut
     * <i>perçu</i>, celui qui est communiqué au membre sur son écran de suivi, et
     * non le statut technique. SUSPENDUE et ATTENTE_VALIDATION_MEMBRE décrivent
     * l'organisation interne de l'atelier. La personne, elle, est informée que son
     * intervention est en cours. Restituer ici les sous-états techniques
     * introduirait une seconde vérité sur la même donnée. Arbitrage à documenter au
     * rapport écrit (article 15 contre RM-16).
     *
     * <p>Le commentaire restitué est {@code commentaireAdmin}, déjà visible du
     * membre dans son suivi. La note interne de l'atelier (A16), qu'il ne voit pas à
     * l'écran, est restituée aussi : elle porte sur lui, l'article 15 la couvre.
     *
     * <p>La plaque arrive en argument, pour la même raison que dans
     * {@link RdvExport} : l'historique d'atelier d'un véhicule retiré du parc reste
     * détenu, il doit rester exporté.
     *
     * <p>Les photos de l'atelier arrivent aussi en argument : elles se rattachent au
     * véhicule d'un membre identifié, l'article 15, § 3 impose d'en remettre une copie.
     * Leurs fichiers sont joints à l'archive, cette section dit où les trouver.
     */
    public record InterventionExport(
            String numero,
            String statut,
            String rendezVous,
            String vehicule,
            Instant debutReel,
            Instant finReelle,
            String commentaireGarage,
            String noteInterneAtelier,
            BigDecimal montantDevisInitialHtva,
            BigDecimal totalFacturableHtva,
            BigDecimal totalFacturableTvac,
            List<LigneInterventionExport> lignes,
            List<PhotoExport> photos) {

        public static InterventionExport de(Intervention intervention, String plaque,
                                            List<PhotoExport> photos) {
            return new InterventionExport(
                    intervention.getNumero(),
                    intervention.getStatut().percuLabel(),
                    intervention.getRdv() == null ? null : intervention.getRdv().getNumero(),
                    plaque,
                    intervention.getDebutReel(),
                    intervention.getFinReelle(),
                    intervention.getCommentaireAdmin(),
                    intervention.getNoteInterne(),
                    intervention.getMontantDevisInitialHtva(),
                    intervention.totalFacturableHtva(),
                    intervention.totalFacturableTvac(),
                    intervention.getLignes().stream().map(LigneInterventionExport::de).toList(),
                    photos);
        }
    }

    /**
     * Photo d'une intervention.
     *
     * @param fichier chemin du fichier dans l'archive, nul si le fichier manque sur le
     *                disque du garage : la photo reste déclarée, sa copie ne peut pas
     *                être jointe
     */
    public record PhotoExport(String fichier, String description) {
    }

    /**
     * Ligne d'intervention. {@code accordMembre} est restitué tel quel, y compris
     * {@code null} : c'est la réponse du membre à une demande de dépassement de
     * devis (RM-15), sa décision, donc sa donnée. {@code null} signifie qu'aucune
     * réponse n'est attendue ni donnée.
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
     * Preuve de consentement. L'<b>adresse IP</b> et l'horodatage sont restitués :
     * ce sont des données à caractère personnel collectées à l'occasion du
     * consentement, elles entrent dans le champ de l'article 15 au même titre que
     * le reste. Les omettre parce qu'elles servent de preuve au responsable du
     * traitement reviendrait à soustraire à la personne une donnée prise sur elle.
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
     * Données de connexion réellement conservées par la V1. Il n'existe pas de
     * journal des tentatives : la table {@code utilisateur} ne garde qu'un
     * <b>compteur</b> courant et l'heure du premier échec de sa fenêtre, remis à zéro à
     * chaque connexion réussie. L'export
     * dit donc ce qui existe, sans laisser croire à un historique.
     *
     * <p>Sont exclus le jeton de vérification et sa date d'expiration : ce sont des
     * secrets d'authentification, communicables à personne, voir {@link Exclusions}.
     * L'adresse en attente de confirmation, elle, EST restituée : c'est une donnée à
     * caractère personnel détenue sur la personne, que l'article 15 couvre au même
     * titre que l'adresse en vigueur. Son jeton reste exclu, pour la même raison que
     * le précédent : communiquer le secret n'est pas communiquer la donnée.
     *
     * <p>Du second facteur, l'export dit s'il est actif et depuis quand, et restitue le
     * journal des événements de sécurité du compte : ce sont des données sur la personne.
     * Le secret TOTP et les codes de secours n'y figurent pas, pour la raison déjà dite
     * des jetons.</p>
     *
     * <p>La date de vérification de l'adresse (V63) est nulle pour un compte vérifié
     * avant cette migration : la base ne l'a jamais connue, et l'export n'en invente
     * pas une.</p>
     */
    public record ConnexionExport(
            Instant derniereConnexion,
            boolean emailVerifie,
            Instant emailVerifieLe,
            String adresseEnAttenteDeConfirmation,
            short tentativesEchoueesEnCours,
            Instant premierEchecEnCoursLe,
            Instant compteVerrouilleJusquA,
            boolean secondFacteurActif,
            Instant secondFacteurActiveLe,
            List<EvenementSecuriteExport> evenementsSecurite) {

        public static ConnexionExport de(Utilisateur membre,
                                         List<EvenementSecuriteCompte> evenements) {
            return new ConnexionExport(
                    membre.getDerniereConnexion(),
                    membre.isEmailVerifie(),
                    membre.getEmailVerifieLe(),
                    membre.getEmailEnAttente(),
                    membre.getTentativesEchouees(),
                    membre.getPremierEchecLe(),
                    membre.getVerrouilleJusquA(),
                    membre.secondFacteurActif(),
                    membre.getMfaActiveLe(),
                    evenements.stream().map(EvenementSecuriteExport::de).toList());
        }
    }

    /**
     * Un événement du journal de sécurité. L'auteur n'est pas restitué : quand ce n'est
     * pas le membre lui-même, c'est un administrateur, dont l'identité n'est pas une
     * donnée du membre.
     */
    public record EvenementSecuriteExport(Instant horodatage, String type) {

        public static EvenementSecuriteExport de(EvenementSecuriteCompte evenement) {
            return new EvenementSecuriteExport(evenement.getHorodatage(), evenement.getType().name());
        }
    }

    /**
     * Une modification du profil ou de l'adresse de courriel. Les valeurs avant et après
     * sont des données sur la personne, comme le motif, qui note sa demande quand un
     * administrateur a agi pour elle. L'administrateur n'est pas nommé, pour la raison
     * dite à {@link EvenementSecuriteExport}.
     */
    public record ModificationCompteExport(Instant horodatage, String champ,
                                           String valeurAvant, String valeurApres,
                                           boolean parAdministrateur, String motif) {

        public static ModificationCompteExport de(ModificationCompte modification) {
            return new ModificationCompteExport(modification.getHorodatage(),
                    modification.getChamp().name(), modification.getValeurAvant(),
                    modification.getValeurApres(), !modification.parTitulaire(),
                    modification.getMotif());
        }
    }

    /** Ce qui ne figure pas dans l'export, et la raison de chaque omission. */
    public record Exclusions(
            String motDePasse,
            String donneesBancaires,
            String secretsTechniques) {
    }
}
