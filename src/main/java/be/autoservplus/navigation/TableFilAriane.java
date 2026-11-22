package be.autoservplus.navigation;

import be.autoservplus.catalogue.service.dto.ArticleVue;
import be.autoservplus.intervention.service.dto.CommandeAPlanifierVue;
import be.autoservplus.intervention.web.dto.DemandeValidationVue;
import be.autoservplus.intervention.web.dto.InterventionVueAdmin;
import be.autoservplus.intervention.web.dto.InterventionVueMembre;
import be.autoservplus.messagerie.service.dto.ConversationVue;
import be.autoservplus.reservation.web.dto.ModificationRdvVue;
import be.autoservplus.reservation.web.dto.RdvVue;
import be.autoservplus.reservation.web.dto.RdvVueAdmin;
import be.autoservplus.vente.web.dto.CommandeAdminDetail;
import be.autoservplus.vente.web.dto.CommandeDetailVue;
import be.autoservplus.vente.web.dto.ConfirmationCommandeVue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Parent et libellé de chaque écran, par motif de route, pour le fil d'Ariane (Charte
 * graphique §6.1).
 *
 * <p>Deux racines, l'accueil et le tableau de bord du back-office, et au plus trois
 * niveaux sous chacune : rubrique, page, détail. Une page plus profonde se rattache à sa
 * rubrique plutôt qu'à la page qui y mène, le déplacement d'un rendez-vous à la liste
 * des rendez-vous et non à son détail : le lien « Retour » de la page garde le chemin
 * exact. {@code FilArianeTest} refuse une entrée qui dépasserait trois niveaux.</p>
 *
 * <p>Le motif est celui du mapping Spring, tel que {@code NavigationEcransIT} le classe :
 * ce test casse la build quand une route d'écran n'a ni entrée ici ni motif dans
 * {@link #SANS_FIL}.</p>
 *
 * <p>La table vit hors de {@code common} : elle cite les vues des modules pour le libellé
 * dynamique d'une page de détail, et {@code common} ne dépend d'aucun module.</p>
 *
 * <p>Pas un bean : la table n'a ni état ni dépendance, et les tests de contrôleur
 * ({@code @WebMvcTest}) chargent l'advice qui l'emploie sans les composants ordinaires.</p>
 */
public class TableFilAriane {

    static final String ACCUEIL = "/";
    static final String BACK_OFFICE = "/admin";

    /** Libellé de chaque racine. */
    static final Map<String, String> RACINES = Map.of(
            ACCUEIL, "fil-ariane.accueil",
            BACK_OFFICE, "admin.tableau.titre");

    /**
     * Écrans sans fil, avec leur motif. Une racine n'a pas de parent à montrer. Les étapes
     * de connexion et les liens reçus par courriel sont des passages obligés, où un lien
     * vers une autre page ferait sortir d'un parcours en cours.
     */
    static final Map<String, String> SANS_FIL = Map.ofEntries(
            Map.entry(ACCUEIL, "racine de l'espace public"),
            Map.entry("/accueil", "autre adresse de l'accueil"),
            Map.entry(BACK_OFFICE, "racine du back-office"),
            Map.entry("/connexion/etape", "étape de connexion"),
            Map.entry("/connexion/etape/second-facteur", "étape de connexion"),
            Map.entry("/connexion/etape/mot-de-passe", "étape de connexion"),
            Map.entry("/connexion/etape/enrolement", "étape de connexion"),
            Map.entry("/inscription/verification", "lien reçu par courriel"),
            Map.entry("/mot-de-passe/nouveau", "lien reçu par courriel"),
            Map.entry("/changement-adresse/confirmation", "lien reçu par courriel"),
            Map.entry("/compte/definition", "lien reçu par courriel"),
            Map.entry("/mes-interventions/depuis-rdv/{rdvReference}", "redirection vers le suivi"));

    /**
     * Un écran du fil.
     *
     * @param cle    clé du libellé, avec {@code {0}} quand {@code valeur} le complète
     * @param parent motif de l'écran parent, une racine au plus haut
     * @param valeur lecture, dans le modèle de la page, de la valeur du libellé
     *               dynamique, ou {@code null} pour un libellé fixe
     */
    record Entree(String patron, String cle, String parent, Function<Map<String, Object>, Object> valeur) {

        boolean dynamique() {
            return valeur != null;
        }
    }

    private final Map<String, Entree> entrees = new LinkedHashMap<>();

    public TableFilAriane() {
        // Espace public
        fixe("/services", "catalogue.titre.services", ACCUEIL);
        fixe("/pieces", "catalogue.titre.pieces", ACCUEIL);
        dynamique("/services/{reference}", "fil-ariane.libre", "/services",
                attribut("article", ArticleVue.class, ArticleVue::libelle));
        dynamique("/pieces/{reference}", "fil-ariane.libre", "/pieces",
                attribut("article", ArticleVue.class, ArticleVue::libelle));
        fixe("/recherche", "fil-ariane.recherche", ACCUEIL);
        fixe("/contact", "vitrine.contact.titre", ACCUEIL);
        fixe("/cookies", "cookies.page.titre", ACCUEIL);
        fixe("/cgv", "legal.cgv.titre", ACCUEIL);
        fixe("/mentions-legales", "legal.mentions.titre", ACCUEIL);
        fixe("/confidentialite", "legal.confidentialite.titre", ACCUEIL);
        fixe("/garantie-legale", "garantie-legale.titre", ACCUEIL);
        fixe("/retractation/formulaire", "fil-ariane.formulaire-retractation", "/cgv");
        fixe("/documents/{type}/{version}", "legal.archive.titre", ACCUEIL);
        fixe("/connexion", "connexion.titre", ACCUEIL);
        fixe("/inscription", "inscription.titre", ACCUEIL);
        fixe("/inscription/confirmee", "inscription.confirmee.titre", "/inscription");
        fixe("/inscription/renvoyer-verification", "verification.renvoi.titre", "/connexion");
        fixe("/mot-de-passe/oublie", "mot-de-passe.oublie.titre", "/connexion");
        fixe("/compte-supprime", "suppression.confirmee.titre", ACCUEIL);
        fixe("/panier", "panier.titre", ACCUEIL);
        fixe("/panier/vider", "panier.vider.titre", "/panier");
        fixe("/commande", "commande.recap.titre", "/panier");

        // Espace membre
        fixe("/mon-compte", "compte.titre", ACCUEIL);
        fixe("/mon-profil", "profil.titre", "/mon-compte");
        fixe("/mon-profil/adresse", "changement-email.titre", "/mon-profil");
        fixe("/mon-profil/mot-de-passe", "mot-de-passe-changement.titre", "/mon-profil");
        fixe("/mon-profil/securite", "securite.titre", "/mon-profil");
        fixe("/mon-profil/securite/enrolement", "enrolement.titre", "/mon-profil");
        fixe("/mon-profil/securite/codes", "codes-secours.titre", "/mon-profil");
        fixe("/mes-vehicules", "vehicule.liste.titre", "/mon-compte");
        fixe("/mes-vehicules/nouveau", "vehicule.formulaire.titre.ajout", "/mes-vehicules");
        fixe("/mes-vehicules/{reference}/modifier", "vehicule.formulaire.titre.modification", "/mes-vehicules");
        fixe("/mes-vehicules/{reference}/historique", "historique.titre", "/mes-vehicules");
        fixe("/mes-rendez-vous", "rdv.liste.titre", "/mon-compte");
        fixe("/mes-rendez-vous/nouveau", "rdv.formulaire.titre", "/mes-rendez-vous");
        dynamique("/mes-rendez-vous/{reference}", "rdv.detail.titre", "/mes-rendez-vous",
                attribut("rdv", RdvVue.class, RdvVue::numero));
        dynamique("/mes-rendez-vous/{reference}/modifier", "rdv.modification.titre", "/mes-rendez-vous",
                attribut("vue", ModificationRdvVue.class, ModificationRdvVue::numero));
        dynamique("/mes-interventions/{reference}", "intervention.suivi.intervention", "/mes-rendez-vous",
                attribut("intervention", InterventionVueMembre.class, InterventionVueMembre::numero));
        dynamique("/mes-interventions/{reference}/validation", "intervention.validation.titre", "/mes-rendez-vous",
                attribut("demande", DemandeValidationVue.class, DemandeValidationVue::numero));
        fixe("/mes-avis/{reference}/nouveau", "avis.formulaire.titre", "/mes-rendez-vous");
        fixe("/mes-messages", "messagerie.titre", "/mon-compte");
        fixe("/mes-messages/nouveau", "messagerie.nouveau.titre", "/mes-messages");
        dynamique("/mes-messages/{reference}", "fil-ariane.libre", "/mes-messages",
                attribut("fil", ConversationVue.class, ConversationVue::sujet));
        fixe("/mes-notifications", "notifications.titre", "/mon-compte");
        fixe("/mes-donnees", "rgpd.donnees.titre", "/mon-compte");
        fixe("/supprimer-mon-compte", "suppression.titre", "/mon-compte");
        fixe("/commandes", "commandes.titre", "/mon-compte");
        dynamique("/commandes/{reference}", "commande.detail.titre", "/commandes",
                attribut("commande", CommandeDetailVue.class, CommandeDetailVue::numero));
        fixe("/commandes/{reference}/annulation", "retractation.titre", "/commandes");
        // Le numéro, et non « Commande enregistrée » : la page sert aussi une commande annulée.
        dynamique("/commande/{reference}/confirmation", "commande.detail.titre", "/commandes",
                attribut("commande", ConfirmationCommandeVue.class, ConfirmationCommandeVue::numero));

        // Back-office
        fixe("/admin/rendez-vous", "admin.rdv.titre", BACK_OFFICE);
        fixe("/admin/rendez-vous/agenda", "admin.rdv.agenda.titre", "/admin/rendez-vous");
        dynamique("/admin/rendez-vous/{reference}", "admin.rdv.detail.titre", "/admin/rendez-vous",
                attribut("rdv", RdvVueAdmin.class, RdvVueAdmin::numero));
        fixe("/admin/rendez-vous/{reference}/refuser", "admin.rdv.refuser.titre", "/admin/rendez-vous");
        fixe("/admin/rendez-vous/{reference}/annuler", "admin.rdv.annuler.titre", "/admin/rendez-vous");
        dynamique("/admin/rendez-vous/{reference}/modifier", "admin.rdv.modification.titre", "/admin/rendez-vous",
                attribut("rdv", RdvVueAdmin.class, RdvVueAdmin::numero));
        fixe("/admin/interventions", "admin.interventions.titre", BACK_OFFICE);
        dynamique("/admin/interventions/{reference}", "intervention.suivi.intervention", "/admin/interventions",
                attribut("intervention", InterventionVueAdmin.class, InterventionVueAdmin::numero));
        fixe("/admin/vehicules/{reference}/historique", "admin.historique.titre", "/admin/interventions");
        fixe("/admin/commandes-a-planifier", "admin.planification.titre", BACK_OFFICE);
        dynamique("/admin/commandes-a-planifier/{reference}", "admin.planification.detail.titre",
                "/admin/commandes-a-planifier",
                attribut("commande", CommandeAPlanifierVue.class, CommandeAPlanifierVue::numero));
        fixe("/admin/catalogue/prestations", "admin.catalogue.prestations.titre", BACK_OFFICE);
        fixe("/admin/catalogue/prestations/nouvelle", "admin.catalogue.prestation.creer.titre",
                "/admin/catalogue/prestations");
        fixe("/admin/catalogue/prestations/{reference}/modifier", "admin.catalogue.prestation.modifier.titre",
                "/admin/catalogue/prestations");
        fixe("/admin/catalogue/prestations/{reference}/supprimer", "admin.catalogue.supprimer.titre",
                "/admin/catalogue/prestations");
        fixe("/admin/catalogue/pieces", "admin.catalogue.pieces.titre", BACK_OFFICE);
        fixe("/admin/catalogue/pieces/nouvelle", "admin.catalogue.piece.creer.titre", "/admin/catalogue/pieces");
        fixe("/admin/catalogue/pieces/{reference}/modifier", "admin.catalogue.piece.modifier.titre",
                "/admin/catalogue/pieces");
        fixe("/admin/catalogue/pieces/{reference}/supprimer", "admin.catalogue.supprimer.titre",
                "/admin/catalogue/pieces");
        fixe("/admin/planning/plages", "admin.planning.plages.titre", BACK_OFFICE);
        fixe("/admin/planning/plages/nouvelle", "admin.planning.plage.creer.titre", "/admin/planning/plages");
        fixe("/admin/planning/plages/{id}/modifier", "admin.planning.plage.modifier.titre", "/admin/planning/plages");
        fixe("/admin/planning/plages/{id}/retirer", "admin.planning.plage.retirer.titre", "/admin/planning/plages");
        fixe("/admin/planning/postes", "admin.planning.postes.titre", BACK_OFFICE);
        fixe("/admin/planning/postes/nouveau", "admin.planning.poste.creer.titre", "/admin/planning/postes");
        fixe("/admin/planning/postes/{reference}/modifier", "admin.planning.poste.modifier.titre",
                "/admin/planning/postes");
        fixe("/admin/planning/postes/{reference}/supprimer", "admin.planning.poste.supprimer.titre",
                "/admin/planning/postes");
        fixe("/admin/planning/indisponibilites", "admin.planning.indisponibilites.titre", BACK_OFFICE);
        fixe("/admin/planning/indisponibilites/nouvelle", "admin.planning.indisponibilite.creer.titre",
                "/admin/planning/indisponibilites");
        fixe("/admin/planning/indisponibilites/{reference}/modifier", "admin.planning.indisponibilite.modifier.titre",
                "/admin/planning/indisponibilites");
        fixe("/admin/planning/indisponibilites/{reference}/retirer", "admin.planning.indisponibilite.retirer.titre",
                "/admin/planning/indisponibilites");
        fixe("/admin/planning/parametres", "admin.parametres.titre", BACK_OFFICE);
        fixe("/admin/galerie/{type}/{reference}", "admin.galerie.titre", BACK_OFFICE);
        fixe("/admin/import", "admin.import.titre", BACK_OFFICE);
        fixe("/admin/export", "admin.export.titre", BACK_OFFICE);
        fixe("/admin/retractations", "admin.retractations.titre", BACK_OFFICE);
        fixe("/admin/retractations/{reference}/refuser", "admin.retractations.refus.titre", "/admin/retractations");
        fixe("/admin/messages", "admin.messagerie.titre", BACK_OFFICE);
        dynamique("/admin/messages/{reference}", "fil-ariane.libre", "/admin/messages",
                attribut("fil", ConversationVue.class, ConversationVue::sujet));
        fixe("/admin/avis", "admin.avis.titre", BACK_OFFICE);
        fixe("/admin/comptes", "admin.comptes.titre", BACK_OFFICE);
        fixe("/admin/comptes/{reference}", "admin.compte.titre", "/admin/comptes");
        fixe("/admin/comptes/{reference}/modification", "admin.compte.modification.titre", "/admin/comptes");
        fixe("/admin/comptes/administrateurs", "admin.administrateurs.titre", "/admin/comptes");
        fixe("/admin/comptes/administrateurs/nouveau", "admin.administrateurs.nouveau.titre",
                "/admin/comptes/administrateurs");
        fixe("/admin/commandes", "admin.ventes.commandes.titre", BACK_OFFICE);
        dynamique("/admin/commandes/{reference}", "admin.ventes.detail.titre", "/admin/commandes",
                attribut("commande", CommandeAdminDetail.class, CommandeAdminDetail::numero));
        fixe("/admin/paiements-a-rembourser", "admin.paiements-tardifs.titre", "/admin/commandes");
        fixe("/admin/paiements-a-rembourser/{reference}", "admin.paiements-tardifs.confirmer.titre",
                "/admin/paiements-a-rembourser");
        fixe("/admin/factures", "admin.ventes.factures.titre", BACK_OFFICE);
        fixe("/admin/avoirs", "admin.ventes.avoirs.titre", BACK_OFFICE);
        fixe("/admin/journal", "admin.journal.titre", BACK_OFFICE);
    }

    Optional<Entree> entree(String patron) {
        return Optional.ofNullable(entrees.get(patron));
    }

    /**
     * Motifs dont le sort est décidé : inscrits au fil, ou sans fil avec leur motif.
     * {@code NavigationEcransIT} y compare les routes d'écran du mapping.
     */
    public Set<String> patronsClasses() {
        Set<String> classes = new TreeSet<>(entrees.keySet());
        classes.addAll(SANS_FIL.keySet());
        return classes;
    }

    Collection<Entree> entrees() {
        return entrees.values();
    }

    /**
     * Les écrans du fil depuis la racine jusqu'à celui-ci, racine exclue, dans l'ordre
     * de lecture. La racine est le parent du premier.
     */
    List<Entree> chemin(Entree depuis) {
        LinkedList<Entree> chemin = new LinkedList<>();
        Entree courante = depuis;
        while (courante != null) {
            chemin.addFirst(courante);
            if (RACINES.containsKey(courante.parent())) {
                break;
            }
            courante = entrees.get(courante.parent());
            if (chemin.size() > entrees.size()) {
                throw new IllegalStateException("Fil d'Ariane cyclique depuis " + depuis.patron());
            }
        }
        return chemin;
    }

    private void fixe(String patron, String cle, String parent) {
        ajouter(new Entree(patron, cle, parent, null));
    }

    private void dynamique(String patron, String cle, String parent, Function<Map<String, Object>, Object> valeur) {
        ajouter(new Entree(patron, cle, parent, valeur));
    }

    private void ajouter(Entree entree) {
        if (entrees.putIfAbsent(entree.patron(), entree) != null) {
            throw new IllegalStateException("Écran inscrit deux fois au fil d'Ariane : " + entree.patron());
        }
    }

    /** Lit une valeur dans le modèle, à condition que l'attribut soit du type attendu. */
    private static <T> Function<Map<String, Object>, Object> attribut(String nom, Class<T> type,
                                                                     Function<T, Object> lecture) {
        return modele -> type.isInstance(modele.get(nom)) ? lecture.apply(type.cast(modele.get(nom))) : null;
    }
}
