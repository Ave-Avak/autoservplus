-- Rattache à sa commande la preuve d'acceptation des CGV et la décision de renonciation
-- (F24, F14).
--
-- La confirmation du contrat joint les CGV acceptées pour cette commande. Jusqu'ici, le
-- lien entre une commande et son acceptation ne tenait qu'au membre et à l'instant :
-- une déduction, là où le versionnage des documents sert à pouvoir dire quel texte a
-- été accepté pour quel achat.
ALTER TABLE consentement ADD COLUMN commande_id BIGINT;

ALTER TABLE consentement
    ADD CONSTRAINT fk_consentement_commande FOREIGN KEY (commande_id)
        REFERENCES commande (id) ON UPDATE CASCADE ON DELETE RESTRICT;

CREATE INDEX ix_consentement_commande ON consentement (commande_id)
    WHERE commande_id IS NOT NULL;

-- Lignes existantes. L'acceptation est écrite dans la transaction de la commande, juste
-- après elle : une ligne CGV ou de renonciation est reliée à la commande du même membre
-- passée au plus cinq secondes avant elle, et seulement si une seule commande répond.
-- Sinon elle reste sans commande, plutôt que d'être rattachée à la mauvaise.
UPDATE consentement c
   SET commande_id = rapprochement.commande_id,
       updated_at = now(),
       updated_by = 'migration V52'
  FROM (SELECT ligne.id AS consentement_id, min(k.id) AS commande_id
          FROM consentement ligne
          JOIN commande k
            ON k.membre_id = ligne.utilisateur_id
           AND ligne.date_consentement >= k.date_commande
           AND ligne.date_consentement < k.date_commande + interval '5 seconds'
         WHERE ligne.type_document IN ('CGV', 'RENONCIATION_RETRACTATION')
         GROUP BY ligne.id
        HAVING count(*) = 1) AS rapprochement
 WHERE c.id = rapprochement.consentement_id;

COMMENT ON COLUMN consentement.commande_id IS
    'Commande pour laquelle les CGV ont été acceptées ou la renonciation décidée. Nulle pour les cookies, et pour une acceptation antérieure qu''aucune commande unique ne rapproche.';
