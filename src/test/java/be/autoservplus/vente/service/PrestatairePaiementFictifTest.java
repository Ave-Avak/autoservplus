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
 * tant qu elle n est pas implementee — jamais exercee par les tests.
 */
@DisplayName("PrestatairePaiement — bouchon et frontiere Mollie")
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
    @DisplayName("le statut relu est celui programme, comme le ferait la relecture Mollie")
    void statutProgrammable() {
        PrestatairePaiementFictif fictif = new PrestatairePaiementFictif();
        PaiementCree cree = fictif.creerPaiement(demande);

        fictif.programmerStatut(cree.referencePrestataire(), StatutPaiement.REUSSI);

        assertThat(fictif.lireEtat(cree.referencePrestataire()).statut())
                .isEqualTo(StatutPaiement.REUSSI);
    }

    @Test
    @DisplayName("une reference inconnue du bouchon est une erreur, pas un statut invente")
    void referenceInconnue() {
        assertThatThrownBy(() -> new PrestatairePaiementFictif().lireEtat("tr_inconnu"))
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


    private static DemandeRemboursement remboursement(PaiementCree cree, String cle) {
        return new DemandeRemboursement(cree.referencePrestataire(),
                new BigDecimal("80.21"), "EUR", cle);
    }
}
