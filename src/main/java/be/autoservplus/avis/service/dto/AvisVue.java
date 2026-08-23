package be.autoservplus.avis.service.dto;

import be.autoservplus.avis.domain.Avis;

import java.util.UUID;

/**
 * Un avis tel qu il s affiche (BL-4).
 *
 * <p><b>Seul le prenom de l auteur est expose</b>, jamais son nom ni son adresse : un
 * avis public ne doit pas permettre d identifier le client du garage. Quand le compte
 * a ete anonymise (F23), le prenom devenu jeton est remplace par un libelle neutre
 * cote gabarit.</p>
 *
 * <p>La date est preformatee, comme {@code CommandeHistoriqueVue} : le gabarit
 * affiche, il ne calcule pas.</p>
 */
public record AvisVue(
        UUID reference,
        String prenomAuteur,
        short note,
        String commentaire,
        String date,
        boolean publie,
        boolean signale,
        String numeroIntervention) {

    public static AvisVue de(Avis avis, String date) {
        return new AvisVue(
                avis.getReference(),
                avis.getMembre().getPrenom(),
                avis.getNote(),
                avis.getCommentaire(),
                date,
                avis.isPublie(),
                avis.isSignale(),
                avis.getIntervention().getNumero());
    }

    public boolean aUnCommentaire() {
        return commentaire != null && !commentaire.isBlank();
    }
}
