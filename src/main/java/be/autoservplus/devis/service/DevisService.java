package be.autoservplus.devis.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.common.temps.FuseauGarage;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.devis.domain.Devis;
import be.autoservplus.devis.repository.DevisRepository;
import be.autoservplus.devis.service.dto.DevisVue;
import be.autoservplus.devis.service.dto.PdfDevis;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.service.VehiculeService;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;
import be.autoservplus.vente.repository.PanierRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Devis du membre connecté (F15) : émission depuis le panier, liste, document PDF.
 *
 * <p>L'identité vient du contexte de sécurité, transmise en adresse par le contrôleur.
 * Le devis d'un autre membre, comme son véhicule, n'est jamais trouvé : 404, la même
 * réponse que pour une référence inconnue.</p>
 *
 * <p>Ni le stock ni la disponibilité d'un créneau ne sont vérifiés : le document dit
 * « sous réserve de disponibilité ». Un article retiré de la vente est en revanche
 * refusé, comme il le serait à la commande : on ne chiffre pas ce qu'on ne vend plus.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class DevisService {

    private final DevisRepository devis;
    private final PanierRepository paniers;
    private final VehiculeService vehicules;
    private final GenerateurNumeroDevis numeros;
    private final GenerateurPdfDevis generateur;
    private final IdentiteGarage identite;
    private final FuseauGarage fuseau;
    private final Clock horloge;

    public DevisService(DevisRepository devis, PanierRepository paniers, VehiculeService vehicules,
                        GenerateurNumeroDevis numeros, GenerateurPdfDevis generateur,
                        IdentiteGarage identite, FuseauGarage fuseau, Clock horloge) {
        this.devis = devis;
        this.paniers = paniers;
        this.vehicules = vehicules;
        this.numeros = numeros;
        this.generateur = generateur;
        this.identite = identite;
        this.fuseau = fuseau;
        this.horloge = horloge;
    }

    /**
     * Émet le devis du panier pour ce véhicule.
     *
     * <p>Langue du document selon la règle des factures : celle du membre si le siège du
     * garage l'admet, sinon la langue par défaut du siège.</p>
     *
     * @return le numéro attribué
     * @throws RefusTraduit                  panier vide, ou article retiré de la vente
     * @throws RessourceIntrouvableException véhicule inconnu, retiré du parc ou d'un autre membre
     */
    @Transactional
    public String emettre(String email, UUID referenceVehicule) {
        Panier panier = paniers.findByMembreEmail(email)
                .filter(trouve -> !trouve.estVide())
                .orElseThrow(() -> new RefusTraduit("devis.erreur.panier-vide",
                        "Un devis ne s'émet pas sur un panier vide."));
        Vehicule vehicule = vehicules.vehiculeDuMembre(referenceVehicule, email);
        if (!vehicule.isActif()) {
            throw new RessourceIntrouvableException("Vehicule", referenceVehicule);
        }
        if (panier.getLignes().stream().anyMatch(ligne -> !articleEnVente(ligne))) {
            throw new RefusTraduit("devis.erreur.article-retire",
                    "Le panier porte un article qui n'est plus proposé à la vente.");
        }
        Instant maintenant = horloge.instant();
        Devis emis = Devis.depuisLePanier(numeros.prochain(), panier, vehicule, maintenant,
                fuseau.jour(maintenant), identite.langueDuDocument(panier.getMembre().getLangue()));
        return devis.save(emis).getNumero();
    }

    /** Devis du membre, le plus récent d'abord, chacun marqué expiré ou non au jour du garage. */
    public List<DevisVue> devisDuMembre(String email) {
        LocalDate aujourdhui = fuseau.jour(horloge.instant());
        return devis.duMembre(email).stream()
                .map(emis -> DevisVue.de(emis, fuseau.zone(), aujourdhui))
                .toList();
    }

    /** Document du devis, recomposé depuis son instantané. */
    public PdfDevis pdf(UUID reference, String email) {
        Devis emis = devis.duMembre(reference, email)
                .orElseThrow(() -> new RessourceIntrouvableException("Devis", reference));
        return new PdfDevis("devis-%s.pdf".formatted(emis.getNumero()),
                generateur.engendrer(emis, fuseau.jour(emis.getDateEmission())));
    }

    private static boolean articleEnVente(LignePanier ligne) {
        return ligne.estService() ? ligne.getPrestation().isActif() : ligne.getPiece().isActif();
    }
}
