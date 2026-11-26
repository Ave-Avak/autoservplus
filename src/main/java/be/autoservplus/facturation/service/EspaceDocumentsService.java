package be.autoservplus.facturation.service;

import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.AvoirRepository;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.facturation.service.dto.EspaceDocumentsVue;
import be.autoservplus.facturation.service.dto.EspaceDocumentsVue.AvoirDuMembre;
import be.autoservplus.facturation.service.dto.EspaceDocumentsVue.DossierDuMembre;
import be.autoservplus.facturation.service.dto.EspaceDocumentsVue.FactureDuMembre;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Espace documents du membre : factures, notes de crédit et interventions réunies sur
 * une page. Chaque document reste téléchargeable par sa route propre, qui refait le
 * contrôle d'appartenance : cette page ne fait que lister.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class EspaceDocumentsService {

    private final FactureRepository factures;
    private final AvoirRepository avoirs;
    private final InterventionRepository interventions;
    private final ParametreAtelierRepository parametres;

    public EspaceDocumentsService(FactureRepository factures, AvoirRepository avoirs,
                                  InterventionRepository interventions,
                                  ParametreAtelierRepository parametres) {
        this.factures = factures;
        this.avoirs = avoirs;
        this.interventions = interventions;
        this.parametres = parametres;
    }

    /**
     * Documents du membre, filtrés sur {@code annee} si le membre a un document cette
     * année-là. Une année absente ou inconnue affiche tout, ce qui évite une page vide
     * sur une adresse modifiée à la main.
     */
    public EspaceDocumentsVue documentsDe(String email, Integer annee) {
        ZoneId zone = parametres.courants().zone();
        List<Avoir> sesAvoirs = avoirs.avoirsDuMembre(email);
        Map<Long, String> avoirParFacture = sesAvoirs.stream()
                .collect(Collectors.toMap(a -> a.getFacture().getId(), Avoir::getNumero));

        List<FactureDuMembre> sesFactures = factures.documentsDuMembre(email).stream()
                .map(f -> vue(f, zone, avoirParFacture.get(f.getId())))
                .toList();
        List<AvoirDuMembre> sesNotes = sesAvoirs.stream()
                .map(a -> new AvoirDuMembre(a.getReference(), a.getNumero(),
                        LocalDate.ofInstant(a.getDateEmission(), zone), a.getFacture().getNumero(),
                        FormatageRdv.eurosEcran(a.getMontantTvac())))
                .toList();
        List<DossierDuMembre> sesDossiers = dossiers(email, zone);

        TreeSet<Integer> annees = new TreeSet<>(Comparator.reverseOrder());
        sesFactures.forEach(f -> annees.add(f.dateEmission().getYear()));
        sesNotes.forEach(a -> annees.add(a.dateEmission().getYear()));
        sesDossiers.forEach(d -> annees.add(d.date().getYear()));

        Integer retenue = annee != null && annees.contains(annee) ? annee : null;
        return new EspaceDocumentsVue(
                filtrer(sesFactures, FactureDuMembre::dateEmission, retenue),
                filtrer(sesNotes, AvoirDuMembre::dateEmission, retenue),
                filtrer(sesDossiers, DossierDuMembre::date, retenue),
                new ArrayList<>(annees), retenue);
    }

    private static FactureDuMembre vue(Facture facture, ZoneId zone, String numeroAvoir) {
        boolean atelier = facture.getIntervention() != null;
        return new FactureDuMembre(facture.getReference(), facture.getNumero(),
                LocalDate.ofInstant(facture.getDateEmission(), zone),
                atelier ? "documents.origine.intervention" : "documents.origine.commande",
                atelier ? facture.getIntervention().getNumero() : facture.getCommande().getNumero(),
                FormatageRdv.eurosEcran(facture.getMontantTvac()), numeroAvoir);
    }

    /**
     * Le véhicule se lit dans la ligne SQL et non par l'entité : un véhicule retiré du
     * parc reste affiché avec ses dossiers.
     */
    private List<DossierDuMembre> dossiers(String email, ZoneId zone) {
        Map<Long, Object[]> vehicules = interventions.dossiersDuTitulaire(email).stream()
                .collect(Collectors.toMap(l -> ((Number) l[0]).longValue(), Function.identity()));
        return interventions.findAllById(vehicules.keySet()).stream()
                .sorted(Comparator.comparing(Intervention::dateDeReference).reversed())
                .map(i -> {
                    Object[] ligne = vehicules.get(i.getId());
                    return new DossierDuMembre(i.getReference(), i.getNumero(),
                            LocalDate.ofInstant(i.dateDeReference(), zone), i.getStatut().clePercue(),
                            "%s %s (%s)".formatted(ligne[2], ligne[3], ligne[1]));
                })
                .toList();
    }

    private static <T> List<T> filtrer(List<T> documents, Function<T, LocalDate> date, Integer annee) {
        return annee == null ? documents
                : documents.stream().filter(d -> date.apply(d).getYear() == annee).toList();
    }
}
