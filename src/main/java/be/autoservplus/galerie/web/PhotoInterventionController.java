package be.autoservplus.galerie.web;

import be.autoservplus.galerie.service.AdminGalerieService;
import be.autoservplus.galerie.service.GalerieService;
import be.autoservplus.galerie.service.TypeGalerie;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.stockage.service.StockageMedia;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Photos d'une intervention, servies au seul titulaire et au back-office.
 *
 * <p>Une photo d'intervention se rattache au véhicule d'un membre identifié : c'est une
 * donnée personnelle, qui ne passe ni par l'adresse publique des images ni par un cache
 * partagé. {@code no-store} : un proxy ou le cache disque d'un poste partagé ne doivent
 * pas la garder après la session.</p>
 *
 * <p>Le titulaire est vérifié avant la photo, et une intervention d'autrui répond comme
 * une référence inconnue (404) : un 403 confirmerait qu'elle existe. La photo doit
 * ensuite appartenir à cette intervention-là.</p>
 */
@Controller
public class PhotoInterventionController {

    private static final CacheControl SANS_CACHE = CacheControl.noStore().cachePrivate();

    private final GalerieService galerie;
    private final AdminGalerieService admin;
    private final InterventionService interventions;
    private final StockageMedia stockage;

    public PhotoInterventionController(GalerieService galerie, AdminGalerieService admin,
                                       InterventionService interventions,
                                       StockageMedia stockage) {
        this.galerie = galerie;
        this.admin = admin;
        this.interventions = interventions;
        this.stockage = stockage;
    }

    @GetMapping("/mes-interventions/{reference}/photos/{photo}")
    public ResponseEntity<byte[]> pourLeTitulaire(@AuthenticationPrincipal UserDetails membre,
                                                  @PathVariable UUID reference,
                                                  @PathVariable UUID photo) {
        interventions.exigerTitulaire(reference, membre.getUsername());
        return ReponseImage.de(stockage, galerie.cheminDIntervention(reference, photo), photo,
                SANS_CACHE);
    }

    @GetMapping("/admin/interventions/{reference}/photos/{photo}")
    public ResponseEntity<byte[]> pourLeGarage(@PathVariable UUID reference,
                                               @PathVariable UUID photo) {
        admin.verifierFiche(TypeGalerie.INTERVENTIONS, reference);
        return ReponseImage.de(stockage, galerie.cheminDIntervention(reference, photo), photo,
                SANS_CACHE);
    }
}
