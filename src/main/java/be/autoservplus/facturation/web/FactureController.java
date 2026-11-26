package be.autoservplus.facturation.web;

import be.autoservplus.common.debit.Plafonne;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.facturation.service.PdfFactureService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Téléchargement de la facture PDF d'une commande payée (F31).
 *
 * <p>La facture est adressée par sa référence publique et l'appartenance est
 * vérifiée au service : la facture d'autrui remonte en 404, jamais en 403. Un 403
 * confirmerait à un tiers que ce numéro de facture existe, alors qu'il s'agit d'un
 * document nominatif. Même mécanisme que le reste du projet, où
 * {@code RessourceIntrouvableException} porte déjà {@code @ResponseStatus(NOT_FOUND)}.</p>
 *
 * <p>L'identité vient du contexte de sécurité, jamais d'un paramètre de requête :
 * sans cela, n'importe qui téléchargerait la facture de n'importe qui en changeant
 * un identifiant dans l'URL.</p>
 *
 * <p>Le PDF est fabriqué à la première demande puis relu de l'archive. La politique
 * appartient au service, le contrôleur ne fait que servir des octets.</p>
 */
@Controller
@RequestMapping("/factures")
public class FactureController {

    private final FactureService factures;
    private final PdfFactureService pdf;

    public FactureController(FactureService factures, PdfFactureService pdf) {
        this.factures = factures;
        this.pdf = pdf;
    }

    @Plafonne("pdf")
    @GetMapping("/{reference}/pdf")
    public ResponseEntity<byte[]> telecharger(@AuthenticationPrincipal UserDetails membre,
                                              @PathVariable UUID reference) {
        Facture facture = factures.pourMembre(reference, membre.getUsername());
        byte[] document = pdf.pdfDe(facture);
        // attachment : le navigateur propose l'enregistrement plutôt que d'ouvrir le
        // document dans la page. Nom de fichier encodé en UTF-8 (ContentDisposition
        // s'en charge) pour les clients qui n'acceptent pas l'ASCII seul.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(pdf.nomDeFichier(facture), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // Document nominatif : aucun cache partagé ne doit le conserver.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(document);
    }
}
