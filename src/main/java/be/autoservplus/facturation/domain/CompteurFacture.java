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
 * Compteur des factures d un exercice comptable, garant de la numerotation
 * <b>continue</b> exigee par la loi belge (AR n°1, art. 5 : suite ininterrompue).
 *
 * <p>Une ligne par annee civile, l exercice servant de cle primaire — la
 * numerotation repart a 1 chaque annee, l annee etant portee par le numero lui-meme.
 * L increment se fait <b>en table</b>, donc dans la transaction qui insere la
 * facture : si celle-ci echoue, l increment est annule avec elle et le numero
 * reste disponible. C est precisement ce qu une sequence PostgreSQL ne sait pas
 * faire — {@code nextval} est non transactionnel par conception et creuse un trou
 * a chaque rollback.</p>
 *
 * <p>Pas de suppression logique ni de {@code created_at} : ce n est pas une entite
 * metier mais un registre technique, calque sur {@code ParametreAtelier} (une seule
 * paire d audit, {@code updated_*}).</p>
 */
@Entity
@Table(name = "compteur_facture")
@EntityListeners(AuditingEntityListener.class)
public class CompteurFacture {

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

    protected CompteurFacture() {
        // requis par JPA
    }

    public CompteurFacture(short exercice) {
        this.exercice = exercice;
        this.dernierNumero = 0;
    }

    /**
     * Consomme le numero suivant de l exercice. Appele sous verrou de ligne : deux
     * emissions concurrentes sont serialisees, la seconde lit la valeur committee
     * par la premiere et ne peut donc pas la reattribuer.
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
        if (!(autre instanceof CompteurFacture compteur)) return false;
        return exercice == compteur.exercice;
    }

    @Override
    public int hashCode() {
        return Short.hashCode(exercice);
    }
}
