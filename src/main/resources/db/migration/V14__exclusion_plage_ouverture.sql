-- Deux plages d'ouverture d'un même jour ne peuvent pas se chevaucher.
-- Une contrainte UNIQUE sur (jour_semaine, heure_debut, heure_fin) serait
-- insuffisante : 08:00-12:00 et 09:00-11:00 sont distincts mais se chevauchent.
-- On projette les colonnes TIME sur une date fixe pour former un tsrange, seul
-- support des opérateurs de chevauchement (&&) exploitables par btree_gist.
--
-- Bornes '[)' : la fin d'une plage et le début d'une autre au même instant sont
-- adjacents et acceptés (ex. 12:00-13:00 puis 13:00-17:00 pour la pause midi).
--
-- Le prédicat ne filtre que deleted_at IS NULL : une plage soft-deletée est
-- sémantiquement absente. En revanche une plage seulement inactive (actif=FALSE)
-- reste présente et doit continuer à occuper son créneau, sinon deux plages
-- pourraient se chevaucher pendant qu'une est désactivée, puis coexister à l'état
-- actif dès sa réactivation sans qu'aucune contrainte ne se déclenche (l'exclusion
-- ne s'évalue qu'à l'écriture). Pour retirer une plage, on la soft-delete.
--
-- Le CHECK heure_fin > heure_debut existe déjà (ck_plage_heures, V3).
-- L'extension btree_gist a été créée en V13.
--
-- Violation renvoyée en SQLSTATE 23P01. La traduction du message vers un texte
-- utilisateur est reportée : elle n'aura de point d'appel que lorsqu'un écran
-- admin des plages permettra la création/modification. Cette traduction reste
-- à faire (aucun chemin d'écriture utilisateur sur plage_ouverture aujourd'hui).

ALTER TABLE plage_ouverture
    ADD CONSTRAINT ex_plage_ouverture_chevauchement
    EXCLUDE USING gist (
        jour_semaine WITH =,
        tsrange('2000-01-01'::date + heure_debut,
                '2000-01-01'::date + heure_fin, '[)') WITH &&
    )
    WHERE (deleted_at IS NULL);
