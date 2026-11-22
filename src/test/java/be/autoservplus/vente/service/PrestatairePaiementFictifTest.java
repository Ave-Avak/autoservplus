package be.autoservplus.vente.service;

import be.autoservplus.vente.domain.StatutPaiement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le bouchon est le prestataire des tests : deterministe, programmable, sans
 * reseau. La passerelle Mollie reelle, elle, doit rester une frontiere fermee
 * tant qu elle n est pas implementee : jamais exercee par les tests.
 */
@DisplayName("PrestatairePaiement : bouchon et frontiere Mollie")
class PrestatairePaiementFictifTest {

    private final DemandePaiement demande = new DemandePaiement(
            "CMD-2026-0001", new BigDecimal("80.21"), "EUR", "cle-idempotence-1",
            "http://localhost:8080/commande/abc/retour",
            "http://localhost:8080/webhooks/paiement");

    @Test
    @DisplayName("le bouchon cree un paiement INITIE avec reference et URL factice")
    void creationFictive() {
        PrestatairePaiementFictif fictif = new PrestatairePaiementFictif();

        PaiementCree cree = fictif.creerPaiement(demande);

        assertThat(cree.referencePrestataire()).startsWith("tr_fictif_");
        assertThat(cree.urlRedirection()).contains(cree.referencePrestataire());
        assertThat(fictif.lireEtat(cree.referencePrestataire()).statut())
                .isEqualTo(StatutPaiement.INITIE);
    }

    @Test
    @DisplayName("un paiement ouvert rend sa page, celle que la creation avait donnee")
    void pageDuPaiementOuvert() {
        PrestatairePaiementFictif fictif = new PrestatairePaiementFictif();
        PaiementCree cree = fictif.creerPaiement(demande);

        assertThat(fictif.lireEtat(cree.referencePrestataire()).urlPaiement())
                .isEqualTo(cree.urlRedirection());
    }

    @Test
    @DisplayName("le statut relu est celui programme, sans page une fois le paiement sorti d'INITIE")
    void statutProgrammable() {
        PrestatairePaiementFictif fictif = new PrestatairePaiementFictif();
        PaiementCree cree = fictif.creerPaiement(demande);

        fictif.programmerStatut(cree.referencePrestataire(), StatutPaiement.EN_COURS);

        EtatPaiement etat = fictif.lireEtat(cree.referencePrestataire());
        assertThat(etat.statut()).isEqualTo(StatutPaiement.EN_COURS);
        assertThat(etat.urlPaiement()).isNull();
    }

    @Test
    @DisplayName("une reference inconnue du bouchon, apres un redemarrage, se relit EXPIRE")
    void referenceInconnue() {
        EtatPaiement etat = new PrestatairePaiementFictif().lireEtat("tr_inconnu");

        assertThat(etat.statut()).isEqualTo(StatutPaiement.EXPIRE);
        assertThat(etat.urlPaiement()).isNull();
    }

    @Test
    @DisplayName("le remboursement refuse toujours une reference inconnue")
    void remboursementReferenceInconnue() {
        assertThatThrownBy(() -> new PrestatairePaiementFictif().rembourser(
                new DemandeRemboursement("tr_inconnu", new java.math.BigDecimal("80.21"), "EUR",
                        "remboursement-x")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("le remboursement bouchonne rend une reference et bascule le paiement (F30)")
    void remboursementFictif() {
        PrestatairePaiementFictif fictif = new PrestatairePaiementFictif();
        PaiementCree cree = fictif.creerPaiement(demande);
        fictif.programmerStatut(cree.referencePrestataire(), StatutPaiement.REUSSI);

        RemboursementCree rembourse = fictif.rembourser(remboursement(cree, "cle-refund-1"));

        assertThat(rembourse.referenceRemboursement()).startsWith("re_fictif_");
        assertThat(fictif.lireEtat(cree.referencePrestataire()).statut())
                .isEqualTo(StatutPaiement.REMBOURSE);
    }

    @Test
    @DisplayName("la cle d'idempotence est honoree : deux appels ne remboursent qu'une fois")
    void remboursementIdempotent() {
        // C est la garantie sur laquelle repose la validation d une retractation :
        // si le commit echoue apres un remboursement accepte, rejouer la validation
        // renvoie la meme cle et le prestataire ne rend pas l argent deux fois.
        PrestatairePaiementFictif fictif = new PrestatairePaiementFictif();
        PaiementCree cree = fictif.creerPaiement(demande);
        fictif.programmerStatut(cree.referencePrestataire(), StatutPaiement.REUSSI);

        RemboursementCree premier = fictif.rembourser(remboursement(cree, "cle-refund-1"));
        RemboursementCree rejeu = fictif.rembourser(remboursement(cree, "cle-refund-1"));

        assertThat(rejeu.referenceRemboursement())
                .isEqualTo(premier.referenceRemboursement());
    }

    @Test
    @DisplayName("le bouchon refuse de rembourser un paiement jamais encaisse")
    void remboursementSansEncaissement() {
        // Le vrai prestataire refuserait aussi : masquer le cas ferait passer des
        // tests sur une chaine impossible en production.
        PrestatairePaiementFictif fictif = new PrestatairePaiementFictif();
        PaiementCree cree = fictif.creerPaiement(demande);

        assertThatThrownBy(() -> fictif.rembourser(remboursement(cree, "cle-refund-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encaisse");
        assertThatThrownBy(() -> fictif.rembourser(new DemandeRemboursement(
                "tr_inconnu", new BigDecimal("80.21"), "EUR", "cle-refund-2")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("deux instances successives, comme deux démarrages, ne rendent jamais la même référence")
    void referencesUniquesAuRedemarrage() {
        // L'ancien compteur repartait de 1 à chaque instance : la première référence du
        // second démarrage était celle du premier, déjà en base.
        java.util.Set<String> paiements = new java.util.HashSet<>();
        java.util.Set<String> remboursementsAccordes = new java.util.HashSet<>();
        for (int demarrage = 0; demarrage < 2; demarrage++) {
            PrestatairePaiementFictif fictif = new PrestatairePaiementFictif();
            for (int i = 0; i < 500; i++) {
                PaiementCree cree = fictif.creerPaiement(demande);
                assertThat(cree.referencePrestataire()).matches("tr_fictif_[0-9a-f]{16}");
                assertThat(paiements.add(cree.referencePrestataire())).isTrue();
                fictif.programmerStatut(cree.referencePrestataire(), StatutPaiement.REUSSI);
                String rembourse = fictif.rembourser(remboursement(cree, "cle-" + demarrage + "-" + i))
                        .referenceRemboursement();
                assertThat(rembourse).matches("re_fictif_[0-9a-f]{16}");
                assertThat(remboursementsAccordes.add(rembourse)).isTrue();
            }
        }
    }

    @Test
    @DisplayName("un paiement ouvert est annulable par défaut, et son annulation le clôt")
    void annulationParDefaut() {
        PrestatairePaiementFictif fictif = new PrestatairePaiementFictif();
        String reference = fictif.creerPaiement(demande).referencePrestataire();
        assertThat(fictif.lireEtat(reference).annulable()).isTrue();

        assertThat(fictif.annuler(reference).statut()).isEqualTo(StatutPaiement.ECHOUE);

        assertThat(fictif.lireEtat(reference).statut()).isEqualTo(StatutPaiement.ECHOUE);
        assertThat(fictif.lireEtat(reference).annulable()).isFalse();
        assertThat(fictif.lireEtat(reference).urlPaiement()).isNull();
    }

    @Test
    @DisplayName("un paiement programmé non annulable refuse l'annulation et reste ouvert")
    void nonAnnulable() {
        PrestatairePaiementFictif fictif = new PrestatairePaiementFictif();
        String reference = fictif.creerPaiement(demande).referencePrestataire();
        fictif.programmerAnnulable(reference, false);

        assertThat(fictif.lireEtat(reference).annulable()).isFalse();
        assertThatThrownBy(() -> fictif.annuler(reference))
                .isInstanceOf(PrestataireIndisponibleException.class);
        assertThat(fictif.lireEtat(reference).statut()).isEqualTo(StatutPaiement.INITIE);
    }

    @Test
    @DisplayName("un paiement réglé ou en cours n'est plus annulable")
    void closNonAnnulable() {
        PrestatairePaiementFictif fictif = new PrestatairePaiementFictif();
        String reference = fictif.creerPaiement(demande).referencePrestataire();

        fictif.programmerStatut(reference, StatutPaiement.EN_COURS);
        assertThat(fictif.lireEtat(reference).annulable()).isFalse();

        fictif.programmerStatut(reference, StatutPaiement.REUSSI);
        assertThatThrownBy(() -> fictif.annuler(reference))
                .isInstanceOf(PrestataireIndisponibleException.class);
        assertThat(fictif.lireEtat(reference).statut()).isEqualTo(StatutPaiement.REUSSI);
    }

    @Test
    @DisplayName("une panne programmée fait échouer la lecture et l'annulation, puis se lève")
    void panneProgrammee() {
        PrestatairePaiementFictif fictif = new PrestatairePaiementFictif();
        String reference = fictif.creerPaiement(demande).referencePrestataire();
        fictif.programmerPanne(reference, true);

        assertThatThrownBy(() -> fictif.lireEtat(reference))
                .isInstanceOf(PrestataireIndisponibleException.class);
        assertThatThrownBy(() -> fictif.annuler(reference))
                .isInstanceOf(PrestataireIndisponibleException.class);

        fictif.programmerPanne(reference, false);
        assertThat(fictif.lireEtat(reference).statut()).isEqualTo(StatutPaiement.INITIE);
    }

    private static DemandeRemboursement remboursement(PaiementCree cree, String cle) {
        return new DemandeRemboursement(cree.referencePrestataire(),
                new BigDecimal("80.21"), "EUR", cle);
    }
}
