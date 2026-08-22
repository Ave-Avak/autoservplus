package be.autoservplus.facturation.web;

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
 * Telechargement de la facture PDF d une commande payee (F31).
 *
 * <p>La facture est adressee par sa reference publique et l appartenance est
 * verifiee au service : la facture d autrui remonte en 404, jamais en 403 — un 403
 * confirmerait a un tiers que ce numero de facture existe, alors qu il s agit d un
 * document nominatif. Meme mecanisme que le reste du projet, ou
 * {@code RessourceIntrouvableException} porte deja {@code @ResponseStatus(NOT_FOUND)}.</p>
 *
 * <p>L identite vient du contexte de securite, jamais d un parametre de requete :
 * sans cela, n importe qui telechargerait la facture de n importe qui en changeant
 * un identifiant dans l URL.</p>
 *
 * <p>Le PDF est fabrique a la premiere demande puis relu de l archive — la politique
 * appartient au service, le controleur ne fait que servir des octets.</p>
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

    @GetMapping("/{reference}/pdf")
    public ResponseEntity<byte[]> telecharger(@AuthenticationPrincipal UserDetails membre,
                                              @PathVariable UUID reference) {
        Facture facture = factures.pourMembre(reference, membre.getUsername());
        byte[] document = pdf.pdfDe(facture);
        // attachment : le navigateur propose l enregistrement plutot que d ouvrir le
        // document dans la page. Nom de fichier encode en UTF-8 (ContentDisposition
        // s en charge) pour les clients qui n acceptent pas l ASCII seul.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(pdf.nomDeFichier(facture), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // Document nominatif : aucun cache partage ne doit le conserver.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(document);
    }
}
