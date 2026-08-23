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
 * Compteur des notes de credit d un exercice comptable (V27).
 *
 * <p>Jumeau de {@link CompteurFacture}, et volontairement <b>distinct</b> de lui :
 * un avoir a sa propre suite legale et ne consomme pas un numero de facture. Les
 * deux suites se lisent separement dans les livres, elles ne s entrelacent pas.</p>
 *
 * <p>Meme mecanique et meme justification qu en V26 : l increment se fait en table,
 * donc dans la transaction qui insere l avoir. Une emission annulee rend son numero
 * au lieu de creuser un trou, ce qu une sequence PostgreSQL ne sait pas faire —
 * {@code nextval} est non transactionnel par conception. La discipline vaut pour le
 * document rectificatif comme pour la facture qu il corrige (AR n°1, art. 5 et 12).</p>
 *
 * <p>Les deux compteurs ne sont pas factorises dans une entite commune : ils vivent
 * dans deux tables distinctes, et une hierarchie JPA (ou une table unique
 * discriminee) rendrait le verrou de l un observable depuis l autre. Deux tables
 * separees serialisent deux flux qui n ont aucune raison de s attendre.</p>
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
     * Consomme le numero suivant de l exercice. Appele sous verrou de ligne : deux
     * emissions concurrentes sont serialisees, la seconde lit la valeur committee par
     * la premiere et ne peut donc pas la reattribuer.
     *
     * @return le numero attribue, strictement croissant a partir de 1
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
