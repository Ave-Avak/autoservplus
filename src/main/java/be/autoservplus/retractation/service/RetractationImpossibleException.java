package be.autoservplus.retractation.service;

/**
 * Le controle automatique refuse la demande de retractation (F30, RM-23).
 *
 * <p>Porte le {@link MotifRefusRetractation} plutot qu un texte : le message
 * utilisateur est fabrique par la couche web, dans la langue de la session. Meme
 * patron que {@code StockInsuffisantException} et {@code PieceInactiveException} du
 * module vente, dont les ecrans sont deja entierement traduits.</p>
 */
public class RetractationImpossibleException extends RuntimeException {

    private final MotifRefusRetractation motif;

    public RetractationImpossibleException(MotifRefusRetractation motif) {
        super("Retractation impossible : " + motif);
        this.motif = motif;
    }

    public MotifRefusRetractation getMotif() {
        return motif;
    }
}
