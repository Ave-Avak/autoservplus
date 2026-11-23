package be.autoservplus.facturation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Compteur des notes de crédit d'un exercice comptable (V27).
 *
 * <p>Jumeau de {@link CompteurFacture}, et volontairement <b>distinct</b> de lui :
 * un avoir a sa propre suite légale et ne consomme pas un numéro de facture. Les
 * deux suites se lisent séparément dans les livres, elles ne s'entrelacent pas.</p>
 *
 * <p>Même mécanique et même justification qu'en V26 : l'incrément se fait en table,
 * donc dans la transaction qui insère l'avoir. Une émission annulée rend son numéro
 * au lieu de creuser un trou, ce qu'une séquence PostgreSQL ne sait pas faire :
 * {@code nextval} est non transactionnel par conception. La discipline vaut pour le
 * document rectificatif comme pour la facture qu'il corrige (AR n°1, art. 5 et 12).</p>
 *
 * <p>Les deux compteurs ne sont pas factorisés dans une entité commune : ils vivent
 * dans deux tables distinctes, et une hiérarchie JPA (ou une table unique
 * discriminée) rendrait le verrou de l'un observable depuis l'autre. Deux tables
 * séparées sérialisent deux flux qui n'ont aucune raison de s'attendre.</p>
 */
@Entity
@Table(name = "compteur_avoir")
@EntityListeners(AuditingEntityListener.class)
public class CompteurAvoir {

    @Id
    @Column(name = "exercice", nullable = false, updatable = false)
    private short exercice;

    @Column(name = "dernier_numero", nullable = false)
    private int dernierNumero;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    protected CompteurAvoir() {
        // requis par JPA
    }

    public CompteurAvoir(short exercice) {
        this.exercice = exercice;
        this.dernierNumero = 0;
    }

    /**
     * Consomme le numéro suivant de l'exercice. Appelé sous verrou de ligne : deux
     * émissions concurrentes sont sérialisées, la seconde lit la valeur committée par
     * la première et ne peut donc pas la réattribuer.
     *
     * @return le numéro attribué, strictement croissant à partir de 1
     */
    public int consommerProchainNumero() {
        return ++dernierNumero;
    }

    public short getExercice() { return exercice; }
    public int getDernierNumero() { return dernierNumero; }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) return true;
        if (!(autre instanceof CompteurAvoir compteur)) return false;
        return exercice == compteur.exercice;
    }

    @Override
    public int hashCode() {
        return Short.hashCode(exercice);
    }
}
