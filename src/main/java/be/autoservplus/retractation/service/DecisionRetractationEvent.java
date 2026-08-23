package be.autoservplus.retractation.service;

import java.util.UUID;

/**
 * Evenement applicatif publie quand l administrateur tranche une demande de
 * retractation (F30), qu il l accepte ou qu il la refuse.
 *
 * <p>Meme modele que {@code CommandePayeeEvent} : la reference seule, jamais
 * l entite — le listener post-commit recharge dans sa propre transaction, et lit
 * alors la decision <b>reellement committee</b> plutot qu un booleen fige au moment
 * de la publication.</p>
 *
 * <p><b>Un seul evenement pour les deux issues</b>, et non un couple
 * validee/refusee : les deux declenchent le meme geste — informer le membre de la
 * decision prise sur son dossier. Le professionnel doit d ailleurs notifier son
 * refus autant que son accord ; scinder l evenement ferait croire qu un refus se
 * traite en silence, ce qui n est pas le cas.</p>
 */
public record DecisionRetractationEvent(UUID referenceDemande) {
}
