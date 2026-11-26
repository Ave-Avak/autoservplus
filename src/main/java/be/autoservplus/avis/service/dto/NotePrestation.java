package be.autoservplus.avis.service.dto;

import java.util.UUID;

/**
 * Note moyenne et nombre d'avis publiés d'une prestation, ligne d'une requête groupée sur
 * plusieurs prestations. {@code Double} et {@code Long} pour la même raison que
 * {@link SyntheseAvis} : c'est ce que rend l'expression de construction pour {@code AVG}
 * et {@code COUNT}.
 */
public record NotePrestation(UUID reference, Double moyenne, Long nombre) {

    public SyntheseAvis synthese() {
        return new SyntheseAvis(moyenne, nombre);
    }
}
