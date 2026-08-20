-- Un creneau ne peut porter qu un seul rendez-vous actif.
-- La contrainte d origine portait sur toutes les lignes : un creneau libere par une
-- annulation devenait definitivement irreservable, alors que l interface le presentait
-- comme disponible. L index partiel restreint l unicite aux rendez-vous en cours.
ALTER TABLE rdv DROP CONSTRAINT uq_rdv_creneau;

CREATE UNIQUE INDEX uq_rdv_creneau_actif
    ON rdv (creneau_id)
    WHERE deleted_at IS NULL
      AND statut IN ('EN_ATTENTE', 'CONFIRME');