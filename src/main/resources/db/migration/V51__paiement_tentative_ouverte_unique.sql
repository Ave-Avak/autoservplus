-- Une seule tentative de paiement ouverte par commande (F14).
--
-- Chaque tentative ouverte est un paiement que le prestataire peut encore encaisser.
-- Le service reprend la tentative ouverte au lieu d'en créer une seconde, sous le
-- verrou de la commande. L'index en fait un invariant de la base, que rien ne
-- contourne, comme ex_rdv_poste_intervalle pour les rendez-vous.
--
-- Avant ce changement, chaque clic sur « payer » créait une tentative. Rien ne clôt
-- celles restées ouvertes sur une commande payée par une autre tentative, et le job
-- RM-21 ne clôt que celles d'une commande annulée. Les tentatives ouvertes d'une
-- commande qui n'attend plus aucun paiement sont donc closes ici. Celles d'une
-- commande encore en attente ne sont pas tranchées à l'aveugle : la migration refuse
-- de s'appliquer tant qu'une telle commande en porte plusieurs. La commande est payée
-- ou annulée par RM-21 dans les trente minutes, puis la migration passe.

DO $$
DECLARE
    en_attente TEXT;
BEGIN
    SELECT string_agg(format('%s (%s tentatives ouvertes)', numero, tentatives), ', '
                      ORDER BY numero)
      INTO en_attente
      FROM (SELECT c.numero, count(*) AS tentatives
              FROM paiement p
              JOIN commande c ON c.id = p.commande_id
             WHERE p.statut IN ('INITIE', 'EN_COURS')
               AND c.statut = 'EN_ATTENTE_PAIEMENT'
             GROUP BY c.id, c.numero
            HAVING count(*) > 1) AS doublons;
    IF en_attente IS NOT NULL THEN
        RAISE EXCEPTION 'V51 non appliquée : commandes en attente de paiement avec plusieurs tentatives ouvertes : %. Attendre leur paiement ou leur annulation (RM-21, trente minutes), puis redémarrer.', en_attente;
    END IF;
END $$;

UPDATE paiement p
   SET statut = 'EXPIRE',
       date_finalisation = now(),
       updated_at = now(),
       updated_by = 'migration V51',
       version = p.version + 1
  FROM commande c
 WHERE c.id = p.commande_id
   AND p.statut IN ('INITIE', 'EN_COURS')
   AND c.statut <> 'EN_ATTENTE_PAIEMENT';

CREATE UNIQUE INDEX uq_paiement_tentative_ouverte
    ON paiement (commande_id)
    WHERE statut IN ('INITIE', 'EN_COURS') AND commande_id IS NOT NULL;

COMMENT ON INDEX uq_paiement_tentative_ouverte IS
    'Au plus une tentative de paiement ouverte (INITIE ou EN_COURS) par commande : une seconde pourrait encaisser deux fois.';
