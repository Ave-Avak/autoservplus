package be.autoservplus.facturation.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.common.web.NumeroPage;
import be.autoservplus.common.web.PeriodeFiltre;
import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.AvoirRepository;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.facturation.service.dto.PieceComptableLigne;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.vente.repository.CommandeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Factures et avoirs consultés depuis le back-office.
 *
 * <p>Le téléchargement réutilise {@code PdfFactureService} et {@code PdfAvoirService} :
 * le document est celui que le membre reçoit, dans sa langue, archivé à la première
 * demande quel que soit celui qui la fait. Un PDF déjà archivé est servi tel quel.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminFacturationService {

    private final FactureRepository factures;
    private final AvoirRepository avoirs;
    private final CommandeRepository commandes;
    private final ParametreAtelierRepository parametres;
    private final Clock horloge;
    private final int taillePage;

    /** @param taillePage lignes par page des listes du back-office, 50 par défaut */
    public AdminFacturationService(FactureRepository factures, AvoirRepository avoirs,
                                   CommandeRepository commandes,
                                   ParametreAtelierRepository parametres, Clock horloge,
                                   @Value("${autoservplus.back-office.taille-page:50}") int taillePage) {
        this.factures = factures;
        this.avoirs = avoirs;
        this.commandes = commandes;
        this.parametres = parametres;
        this.horloge = horloge;
        this.taillePage = taillePage;
    }

    /** Période saisie, complétée par les trente derniers jours de l'atelier. */
    public PeriodeFiltre periode(LocalDate du, LocalDate au) {
        return PeriodeFiltre.de(du, au, LocalDate.ofInstant(horloge.instant(), zone()));
    }

    public Page<PieceComptableLigne> factures(PeriodeFiltre periode, String recherche, int page) {
        PageRequest demande = PageRequest.of(NumeroPage.borne(page, taillePage), taillePage);
        if (periode.inversee()) {
            return Page.empty(demande);
        }
        ZoneId zone = zone();
        return factures.rechercherAdmin(periode.debutPour(recherche, zone), periode.finPour(recherche, zone),
                        motif(recherche), demande)
                .map(facture -> PieceComptableLigne.deFacture(facture, zone));
    }

    public Page<PieceComptableLigne> avoirs(PeriodeFiltre periode, String recherche, int page) {
        PageRequest demande = PageRequest.of(NumeroPage.borne(page, taillePage), taillePage);
        if (periode.inversee()) {
            return Page.empty(demande);
        }
        ZoneId zone = zone();
        return avoirs.rechercherAdmin(periode.debutPour(recherche, zone), periode.finPour(recherche, zone),
                        motif(recherche), demande)
                .map(avoir -> PieceComptableLigne.deAvoir(avoir, zone));
    }

    /** Facture de la commande, pour son détail au back-office. */
    public Optional<PieceComptableLigne> factureDeLaCommande(UUID referenceCommande) {
        ZoneId zone = zone();
        return commandes.findByReference(referenceCommande)
                .flatMap(factures::findByCommande)
                .map(facture -> PieceComptableLigne.deFacture(facture, zone));
    }

    /** Avoir de la commande, s'il en existe un : au plus un par facture (V27). */
    public Optional<PieceComptableLigne> avoirDeLaCommande(UUID referenceCommande) {
        ZoneId zone = zone();
        return commandes.findByReference(referenceCommande)
                .flatMap(factures::findByCommande)
                .flatMap(avoirs::findByFacture)
                .map(avoir -> PieceComptableLigne.deAvoir(avoir, zone));
    }

    public Facture facture(UUID reference) {
        return factures.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Facture", reference.toString()));
    }

    public Avoir avoir(UUID reference) {
        return avoirs.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Avoir", reference.toString()));
    }

    private ZoneId zone() {
        return parametres.courants().zone();
    }

    private static String motif(String recherche) {
        return recherche == null || recherche.isBlank()
                ? "%"
                : "%" + recherche.strip().toLowerCase(Locale.ROOT) + "%";
    }
}
