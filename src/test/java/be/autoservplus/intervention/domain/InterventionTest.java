package be.autoservplus.intervention.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de l entite Intervention : construction depuis un RDV (pre-remplissage
 * des lignes de main d oeuvre), machine a etats alignee sur le CdC
 * (table 3.8 du dictionnaire), gestion des lignes, totaux.
 */
@DisplayName("Intervention")
class InterventionTest {

    private static final Instant DEBUT = Instant.parse("2026-09-14T08:00:00Z");
    private static final Duration PAS = Duration.ofMinutes(30);

    private final Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    private final Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
    private final PosteAtelier pont = new PosteAtelier("Pont 1");
    private final Categorie entretien = new Categorie("ENT", "Entretien", TypeCategorie.SERVICE);
    private final Prestation vidange = new Prestation(entretien, "VID", "Vidange", new BigDecimal("49.00"), 30);
    private final Prestation freins = new Prestation(entretien, "FRE", "Plaquettes", new BigDecimal("89.00"), 60);

    private Rdv rdvAvec(Prestation... prestations) {
        return new Rdv("RDV-2026-0001", marie, golf, pont, DEBUT, PAS, List.of(prestations), null);
    }

    private Intervention interventionDepuis(Prestation... prestations) {
        return new Intervention("INT-2026-0001", rdvAvec(prestations));
    }

    @Nested
    @DisplayName("construction depuis un RDV")
    class Construction {

        @Test
        @DisplayName("statut PLANIFIEE, reference et vehicule recopies")
        void demarreEnPlanifiee() {
            Intervention it = interventionDepuis(vidange);

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.PLANIFIEE);
            assertThat(it.getReference()).isNotNull();
            assertThat(it.getNumero()).isEqualTo("INT-2026-0001");
            assertThat(it.getVehicule()).isEqualTo(golf);
            assertThat(it.getRdv().getReference()).isNotNull();
        }

        @Test
        @DisplayName("une ligne de main d oeuvre par prestation, libelle et prix figes")
        void preRemplitLesLignes() {
            Intervention it = interventionDepuis(vidange, freins);

            assertThat(it.getLignes()).hasSize(2);
            assertThat(it.getLignes()).allMatch(l -> l.getType() == TypeLigneIntervention.MAIN_OEUVRE);
            assertThat(it.getLignes().get(0).getLibelleFige()).isEqualTo("Vidange");
            assertThat(it.getLignes().get(0).getPrixUnitaireHtva()).isEqualByComparingTo("49.00");
        }

        @Test
        @DisplayName("figer le prix : une modification ulterieure du catalogue n affecte pas la ligne")
        void figeLePrix() {
            Intervention it = interventionDepuis(vidange);
            vidange.modifierPrix(new BigDecimal("59.00"));

            assertThat(it.getLignes().get(0).getPrixUnitaireHtva()).isEqualByComparingTo("49.00");
        }
    }

    @Nested
    @DisplayName("transitions autorisees")
    class Transitions {

        @Test
        @DisplayName("PLANIFIEE -> EN_COURS enregistre le debut reel")
        void demarrer() {
            Intervention it = interventionDepuis(vidange);
            Instant maintenant = DEBUT.plus(Duration.ofHours(1));

            it.demarrer(maintenant);

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
            assertThat(it.getDebutReel()).isEqualTo(maintenant);
        }

        @Test
        @DisplayName("EN_COURS -> SUSPENDUE ne modifie pas le debut")
        void suspendre() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            Instant debutFige = it.getDebutReel();

            it.suspendre();

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.SUSPENDUE);
            assertThat(it.getDebutReel()).isEqualTo(debutFige);
        }

        @Test
        @DisplayName("SUSPENDUE -> EN_COURS (reprendre) ne remet pas le debut a jour")
        void reprendreDepuisSuspendue() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            Instant debutFige = it.getDebutReel();
            it.suspendre();

            it.reprendre();

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
            assertThat(it.getDebutReel()).isEqualTo(debutFige);
        }

        @Test
        @DisplayName("EN_COURS -> TERMINEE enregistre la fin reelle")
        void terminerDepuisEnCours() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            Instant fin = DEBUT.plus(Duration.ofHours(1));

            it.terminer(fin);

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.TERMINEE);
            assertThat(it.getFinReelle()).isEqualTo(fin);
        }

        @Test
        @DisplayName("annuler depuis PLANIFIEE bascule en ANNULEE")
        void annulerDepuisPlanifiee() {
            Intervention it = interventionDepuis(vidange);

            it.annuler();

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.ANNULEE);
        }

        @Test
        @DisplayName("annuler depuis EN_COURS bascule en ANNULEE")
        void annulerDepuisEnCours() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);

            it.annuler();

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.ANNULEE);
        }

        @Test
        @DisplayName("annuler depuis SUSPENDUE bascule en ANNULEE")
        void annulerDepuisSuspendue() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            it.suspendre();

            it.annuler();

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.ANNULEE);
        }
    }

    @Nested
    @DisplayName("transitions interdites")
    class TransitionsInterdites {

        @Test
        @DisplayName("PLANIFIEE ne peut pas etre suspendue directement")
        void planifieeVersSuspendue() {
            Intervention it = interventionDepuis(vidange);
            assertThatThrownBy(it::suspendre)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PLANIFIEE");
        }

        @Test
        @DisplayName("PLANIFIEE ne peut PAS aller directement en TERMINEE (raccourci express retire, CdC)")
        void planifieeVersTermineeRefusee() {
            Intervention it = interventionDepuis(vidange);
            assertThatThrownBy(() -> it.terminer(DEBUT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PLANIFIEE");
        }

        @Test
        @DisplayName("self-loops refuses pour tous les statuts")
        void selfLoopsRefuses() {
            for (StatutIntervention s : StatutIntervention.values()) {
                assertThat(s.peutPasserA(s))
                        .as("Aucun statut ne doit pouvoir transiter vers lui-meme : " + s)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("double demarrage refuse")
        void doubleDemarrage() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            assertThatThrownBy(() -> it.demarrer(DEBUT.plusSeconds(1)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("TERMINEE est terminal : aucune sortie, meme pour correction")
        void termineeDefinitive() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            it.terminer(DEBUT.plus(Duration.ofHours(1)));

            assertThatThrownBy(it::suspendre).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(it::reprendre).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(it::annuler).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ANNULEE est terminal : aucune sortie")
        void annuleeDefinitive() {
            Intervention it = interventionDepuis(vidange);
            it.annuler();

            assertThatThrownBy(it::reprendre).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> it.terminer(DEBUT)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(it::annuler).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("statut percu par le membre (RM-16)")
    class Percu {

        @Test
        @DisplayName("PLANIFIEE -> En attente")
        void planifieeEstEnAttente() {
            assertThat(StatutIntervention.PLANIFIEE.percuLabel()).isEqualTo("En attente");
        }

        @Test
        @DisplayName("EN_COURS, SUSPENDUE et ATTENTE_VALIDATION_MEMBRE -> En cours (mecanique interne masquee)")
        void enCoursEtInternesSontEnCours() {
            assertThat(StatutIntervention.EN_COURS.percuLabel()).isEqualTo("En cours");
            assertThat(StatutIntervention.SUSPENDUE.percuLabel())
                    .as("Le membre ne doit pas voir « Suspendue » : le travail continue de son cote")
                    .isEqualTo("En cours");
            assertThat(StatutIntervention.ATTENTE_VALIDATION_MEMBRE.percuLabel())
                    .as("Le membre ne doit pas voir « ATTENTE_VALIDATION_MEMBRE » : notification via canal dedie")
                    .isEqualTo("En cours");
        }

        @Test
        @DisplayName("TERMINEE -> Terminée")
        void termineeEstTerminee() {
            assertThat(StatutIntervention.TERMINEE.percuLabel()).isEqualTo("Terminée");
        }

        @Test
        @DisplayName("ANNULEE -> Annulée (cas terminal explicite pour le membre)")
        void annuleeEstAnnulee() {
            assertThat(StatutIntervention.ANNULEE.percuLabel()).isEqualTo("Annulée");
        }

        @Test
        @DisplayName("percuLabel couvre les 6 statuts techniques sur 4 valeurs percues")
        void projectionSurQuatreValeurs() {
            java.util.Set<String> percus = new java.util.HashSet<>();
            for (StatutIntervention s : StatutIntervention.values()) {
                percus.add(s.percuLabel());
            }
            assertThat(percus).containsExactlyInAnyOrder("En attente", "En cours", "Terminée", "Annulée");
        }
    }

    @Nested
    @DisplayName("machine a etats (niveau enum)")
    class MachineEnum {

        @Test
        @DisplayName("EN_COURS peut basculer vers ATTENTE_VALIDATION_MEMBRE (RM-15)")
        void enCoursVersAttenteValidation() {
            assertThat(StatutIntervention.EN_COURS.peutPasserA(StatutIntervention.ATTENTE_VALIDATION_MEMBRE))
                    .isTrue();
        }

        @Test
        @DisplayName("ATTENTE_VALIDATION_MEMBRE retourne en EN_COURS ou passe en ANNULEE (reversibilite membre)")
        void attenteValidationReversible() {
            StatutIntervention s = StatutIntervention.ATTENTE_VALIDATION_MEMBRE;
            assertThat(s.peutPasserA(StatutIntervention.EN_COURS)).isTrue();
            assertThat(s.peutPasserA(StatutIntervention.ANNULEE)).isTrue();
            // les autres sont refusees
            assertThat(s.peutPasserA(StatutIntervention.PLANIFIEE)).isFalse();
            assertThat(s.peutPasserA(StatutIntervention.SUSPENDUE)).isFalse();
            assertThat(s.peutPasserA(StatutIntervention.TERMINEE)).isFalse();
        }

        @Test
        @DisplayName("SUSPENDUE peut basculer vers ATTENTE_VALIDATION_MEMBRE (depassement chiffre a l'arret)")
        void suspendueVersAttenteValidation() {
            StatutIntervention s = StatutIntervention.SUSPENDUE;
            assertThat(s.peutPasserA(StatutIntervention.ATTENTE_VALIDATION_MEMBRE)).isTrue();
            assertThat(s.peutPasserA(StatutIntervention.EN_COURS)).isTrue();
            assertThat(s.peutPasserA(StatutIntervention.ANNULEE)).isTrue();
            assertThat(s.peutPasserA(StatutIntervention.TERMINEE))
                    .as("On ne termine pas une intervention suspendue sans la reprendre")
                    .isFalse();
        }

        @Test
        @DisplayName("PLANIFIEE -> ANNULEE autorise, PLANIFIEE -> SUSPENDUE / TERMINEE / ATTENTE... refuses")
        void planifieeTransitions() {
            StatutIntervention s = StatutIntervention.PLANIFIEE;
            assertThat(s.peutPasserA(StatutIntervention.EN_COURS)).isTrue();
            assertThat(s.peutPasserA(StatutIntervention.ANNULEE)).isTrue();
            assertThat(s.peutPasserA(StatutIntervention.SUSPENDUE)).isFalse();
            assertThat(s.peutPasserA(StatutIntervention.TERMINEE)).isFalse();
            assertThat(s.peutPasserA(StatutIntervention.ATTENTE_VALIDATION_MEMBRE)).isFalse();
        }
    }

    @Nested
    @DisplayName("lignes et commentaire admin")
    class Editions {

        @Test
        @DisplayName("ajouterLigneMainOeuvre possible en PLANIFIEE, EN_COURS, SUSPENDUE")
        void ajouterLigneQuandEditable() {
            // Montants volontairement faibles : ce test porte sur l editabilite par
            // statut, pas sur le seuil RM-15 (couvert par DepassementDevis). Un montant
            // eleve basculerait en ATTENTE_VALIDATION_MEMBRE et masquerait l intention.
            BigDecimal petitPrix = new BigDecimal("1.00");
            Intervention it = interventionDepuis(vidange);
            it.ajouterLigneMainOeuvre(freins, (short) 1, petitPrix, new BigDecimal("21.00"));
            assertThat(it.getLignes()).hasSize(2);

            it.demarrer(DEBUT);
            it.ajouterLigneMainOeuvre(freins, (short) 1, petitPrix, new BigDecimal("21.00"));
            assertThat(it.getLignes()).hasSize(3);

            it.suspendre();
            it.ajouterLigneMainOeuvre(freins, (short) 1, petitPrix, new BigDecimal("21.00"));
            assertThat(it.getLignes()).hasSize(4);
            assertThat(it.getStatut()).isEqualTo(StatutIntervention.SUSPENDUE);
        }

        @Test
        @DisplayName("ajouterLigne refuse en TERMINEE (etat terminal, facturation branchee au module V2)")
        void ajouterLigneRefuseQuandTerminee() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            it.terminer(DEBUT.plus(Duration.ofHours(1)));

            assertThatThrownBy(() -> it.ajouterLigneMainOeuvre(freins, (short) 1,
                    new BigDecimal("89.00"), new BigDecimal("21.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TERMINEE");
        }

        @Test
        @DisplayName("ajouterLigne refuse en ANNULEE")
        void ajouterLigneRefuseQuandAnnulee() {
            Intervention it = interventionDepuis(vidange);
            it.annuler();

            assertThatThrownBy(() -> it.ajouterLigneMainOeuvre(freins, (short) 1,
                    new BigDecimal("89.00"), new BigDecimal("21.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ANNULEE");
        }

        @Test
        @DisplayName("modifierCommentaireAdmin nettoie et respecte estEditable")
        void commentaireAdmin() {
            Intervention it = interventionDepuis(vidange);
            it.modifierCommentaireAdmin("  Piece commandee ");
            assertThat(it.getCommentaireAdmin()).isEqualTo("Piece commandee");

            it.modifierCommentaireAdmin("   ");
            assertThat(it.getCommentaireAdmin()).isNull();

            it.demarrer(DEBUT);
            it.terminer(DEBUT.plus(Duration.ofHours(1)));
            // TERMINEE est terminal : plus d edition possible.
            assertThatThrownBy(() -> it.modifierCommentaireAdmin("trop tard"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    /**
     * RM-15 : « un depassement de PLUS DE dix pour cent du devis exige un accord
     * expres du client avant poursuite ». Le devis de reference est la vidange seule
     * (49,00 € HTVA), le seuil vaut donc 53,90 € — la borne exacte est testee dans les
     * deux sens, c est elle qui distingue une lecture stricte d une lecture large.
     */
    @Nested
    @DisplayName("depassement de devis (RM-15)")
    class DepassementDevis {

        private static final BigDecimal TVA = new BigDecimal("21.00");

        private Intervention enCoursAvecDevisDe49() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            return it;
        }

        private LigneIntervention ajouter(Intervention it, String prixHtva) {
            return it.ajouterLigneMainOeuvre(freins, (short) 1, new BigDecimal(prixHtva), TVA);
        }

        @Test
        @DisplayName("le seuil vaut le devis majore de 10 %, en HTVA")
        void seuilCalcule() {
            assertThat(enCoursAvecDevisDe49().seuilDepassementHtva()).isEqualByComparingTo("53.90");
        }

        @Test
        @DisplayName("total PILE a 110 % du devis : PAS de validation requise (« plus de » = strict)")
        void seuilExactNeDeclenchePas() {
            Intervention it = enCoursAvecDevisDe49();

            LigneIntervention ligne = ajouter(it, "4.90"); // 49.00 + 4.90 = 53.90 = seuil exact

            assertThat(it.totalFacturableHtva())
                    .as("Le total doit tomber exactement sur le seuil")
                    .isEqualByComparingTo(it.seuilDepassementHtva())
                    .isEqualByComparingTo("53.90");
            assertThat(it.getStatut())
                    .as("A 110 % pile on n'est pas « au-dela de 10 % » : le garage continue")
                    .isEqualTo(StatutIntervention.EN_COURS);
            assertThat(ligne.isValidee()).isTrue();
            assertThat(it.aDesLignesEnAttente()).isFalse();
        }

        @Test
        @DisplayName("un centime au-dessus du seuil : bascule en ATTENTE_VALIDATION_MEMBRE")
        void unCentimeAuDessusDeclenche() {
            Intervention it = enCoursAvecDevisDe49();

            LigneIntervention ligne = ajouter(it, "4.91"); // 53.91 > 53.90

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.ATTENTE_VALIDATION_MEMBRE);
            assertThat(ligne.estEnAttente()).isTrue();
            assertThat(it.totalFacturableHtva())
                    .as("Tant que le membre n'a pas repondu, la ligne ne compte pas")
                    .isEqualByComparingTo("49.00");
            assertThat(it.totalProposeHtva()).isEqualByComparingTo("53.91");
        }

        @Test
        @DisplayName("petit ajout sous le seuil : ligne validee d office, aucune friction")
        void ajoutSousLeSeuil() {
            Intervention it = enCoursAvecDevisDe49();

            LigneIntervention ligne = ajouter(it, "2.00"); // 51.00 <= 53.90

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
            assertThat(ligne.isValidee()).isTrue();
            assertThat(it.totalFacturableHtva()).isEqualByComparingTo("51.00");
        }

        @Test
        @DisplayName("cumulatif : trois ajouts sous le seuil chacun finissent par le franchir")
        void seuilCumulatif() {
            Intervention it = enCoursAvecDevisDe49();

            ajouter(it, "2.00");  // 51.00
            assertThat(it.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
            ajouter(it, "2.00");  // 53.00
            assertThat(it.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
            ajouter(it, "2.00");  // 55.00 > 53.90

            assertThat(it.getStatut())
                    .as("La comparaison porte sur le total cumule, pas sur l'apport de la ligne")
                    .isEqualTo(StatutIntervention.ATTENTE_VALIDATION_MEMBRE);
        }

        @Test
        @DisplayName("le membre accepte : lignes validees, EN_COURS, total facturable elargi")
        void membreAccepte() {
            Intervention it = enCoursAvecDevisDe49();
            LigneIntervention ligne = ajouter(it, "89.00");

            it.validerDepassement();

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
            assertThat(ligne.isValidee()).isTrue();
            assertThat(ligne.isRefusee()).isFalse();
            assertThat(it.totalFacturableHtva()).isEqualByComparingTo("138.00");
            assertThat(it.aDesLignesEnAttente()).isFalse();
        }

        @Test
        @DisplayName("le membre refuse : lignes conservees, hors total, EN_COURS sur le perimetre initial")
        void membreRefuse() {
            Intervention it = enCoursAvecDevisDe49();
            LigneIntervention ligne = ajouter(it, "89.00");

            it.refuserDepassement();

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
            assertThat(it.getLignes())
                    .as("La ligne refusee reste au dossier : trace du defaut constate")
                    .hasSize(2)
                    .contains(ligne);
            assertThat(ligne.isRefusee()).isTrue();
            assertThat(ligne.estFacturable()).isFalse();
            assertThat(it.totalFacturableHtva())
                    .as("Retour au perimetre du devis initial")
                    .isEqualByComparingTo("49.00");
            assertThat(it.totalProposeHtva())
                    .as("Une ligne refusee sort aussi du total propose")
                    .isEqualByComparingTo("49.00");
            assertThat(it.aDesLignesEnAttente()).isFalse();
        }

        @Test
        @DisplayName("le garage ne peut pas reprendre tant que le membre n'a pas repondu")
        void garageBloquePendantAttente() {
            Intervention it = enCoursAvecDevisDe49();
            ajouter(it, "89.00");

            assertThatThrownBy(it::reprendre)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RM-15");

            // ... et redevient possible des que le membre a tranche.
            it.refuserDepassement();
            assertThat(it.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
        }

        @Test
        @DisplayName("une ligne ajoutee pendant l'attente rejoint le lot, sans nouvelle bascule")
        void ajoutPendantAttenteRejointLeLot() {
            Intervention it = enCoursAvecDevisDe49();
            ajouter(it, "89.00");
            assertThat(it.getStatut()).isEqualTo(StatutIntervention.ATTENTE_VALIDATION_MEMBRE);

            LigneIntervention seconde = ajouter(it, "10.00");

            assertThat(seconde.estEnAttente()).isTrue();
            assertThat(it.lignesEnAttente()).hasSize(2);
            assertThat(it.getStatut()).isEqualTo(StatutIntervention.ATTENTE_VALIDATION_MEMBRE);
            assertThat(it.totalProposeHtva()).isEqualByComparingTo("148.00");
        }

        @Test
        @DisplayName("depassement constate en suspension : bascule aussi depuis SUSPENDUE")
        void depassementDepuisSuspendue() {
            Intervention it = enCoursAvecDevisDe49();
            it.suspendre();

            ajouter(it, "89.00");

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.ATTENTE_VALIDATION_MEMBRE);
        }

        @Test
        @DisplayName("PLANIFIEE hors dispositif : rien n'a commence, aucune « poursuite » a garder")
        void planifieeHorsDispositif() {
            Intervention it = interventionDepuis(vidange); // reste PLANIFIEE

            LigneIntervention ligne = ajouter(it, "500.00");

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.PLANIFIEE);
            assertThat(ligne.isValidee()).isTrue();
        }

        @Test
        @DisplayName("repondre hors ATTENTE_VALIDATION_MEMBRE est refuse (double soumission)")
        void reponseHorsContexteRefusee() {
            Intervention it = enCoursAvecDevisDe49();

            assertThatThrownBy(it::validerDepassement)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("EN_COURS");
            assertThatThrownBy(it::refuserDepassement)
                    .isInstanceOf(IllegalStateException.class);

            ajouter(it, "89.00");
            it.validerDepassement();
            // deuxieme envoi (retour arriere du navigateur) : la demande est tranchee.
            assertThatThrownBy(it::validerDepassement)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("totaux")
    class Totaux {

        @Test
        @DisplayName("additionne les lignes HTVA et TVAC")
        void additionneLesLignes() {
            Intervention it = interventionDepuis(vidange, freins);

            assertThat(it.totalFacturableHtva()).isEqualByComparingTo("138.00");
            assertThat(it.totalFacturableTvac()).isEqualByComparingTo("166.98");
        }

        @Test
        @DisplayName("le devis initial est fige a la creation, en HTVA, depuis les lignes du RDV")
        void devisInitialFigeEnHtva() {
            Intervention it = interventionDepuis(vidange, freins);

            // 49.00 + 89.00, hors TVA : le seuil RM-15 se calcule sur le HT.
            assertThat(it.getMontantDevisInitialHtva()).isEqualByComparingTo("138.00");
            assertThat(it.totalDevisInitialHtva()).isEqualByComparingTo("138.00");
            assertThat(it.devisReferenceHtva()).isEqualByComparingTo("138.00");
            assertThat(it.getMontantDevisInitialHtva())
                    .as("Le devis doit etre du HTVA, jamais du TVAC")
                    .isNotEqualByComparingTo(it.totalFacturableTvac());
        }

        @Test
        @DisplayName("les lignes du RDV naissent validees, non refusees, non ajoutees en cours")
        void lignesDuRdvValideesDOffice() {
            Intervention it = interventionDepuis(vidange, freins);

            assertThat(it.getLignes()).allSatisfy(l -> {
                assertThat(l.isAjouteeEnCours()).isFalse();
                assertThat(l.isValidee()).isTrue();
                assertThat(l.isRefusee()).isFalse();
                assertThat(l.estFacturable()).isTrue();
            });
        }

        @Test
        @DisplayName("une ligne ajoutee par le garage est marquee ajouteeEnCours")
        void ligneAjouteeEnCoursMarquee() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);

            // Montant sous le seuil RM-15 : on teste ici le marquage, pas la bascule.
            LigneIntervention ajoutee = it.ajouterLigneMainOeuvre(freins, (short) 1,
                    new BigDecimal("2.00"), new BigDecimal("21.00"));

            assertThat(ajoutee.isAjouteeEnCours()).isTrue();
            assertThat(ajoutee.isValidee()).isTrue();
            assertThat(ajoutee.isRefusee()).isFalse();
        }

        @Test
        @DisplayName("le devis initial ne bouge pas quand le garage ajoute une ligne")
        void devisInitialInsensibleAuxAjouts() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            it.ajouterLigneMainOeuvre(freins, (short) 1,
                    new BigDecimal("2.00"), new BigDecimal("21.00"));

            assertThat(it.devisReferenceHtva())
                    .as("La base de comparaison RM-15 doit rester le devis d origine")
                    .isEqualByComparingTo("49.00");
            assertThat(it.totalFacturableHtva()).isEqualByComparingTo("51.00");
        }

        // L exclusion d une ligne refusee du total est desormais verifiee de bout en
        // bout par DepassementDevis.membreRefuse, qui passe par le chemin reel
        // (ajout > seuil, puis refus du membre) plutot que par un marquage direct.

        @Test
        @DisplayName("retirerLigne refuse en TERMINEE")
        void retirerLigneRefuseQuandTerminee() {
            Intervention it = interventionDepuis(vidange);
            it.demarrer(DEBUT);
            it.terminer(DEBUT.plus(Duration.ofHours(1)));

            assertThatThrownBy(() -> it.retirerLigne(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TERMINEE");
        }
    }
}
