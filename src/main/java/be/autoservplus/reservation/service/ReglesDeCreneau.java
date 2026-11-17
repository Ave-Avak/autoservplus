package be.autoservplus.reservation.service;

/**
 * Règles de temps qu'un créneau doit respecter selon qui le choisit.
 *
 * <p>Plages d'ouverture, jours fériés, indisponibilités et postes libres valent pour
 * tous. Le délai minimal, l'horizon et, en mode manuel, le temps laissé au garage pour
 * répondre protègent l'atelier contre une demande du membre. Ils n'ont pas de sens
 * quand c'est le garage qui fixe le créneau : seul compte qu'il soit encore à venir.</p>
 */
public enum ReglesDeCreneau {
    MEMBRE,
    GARAGE
}
