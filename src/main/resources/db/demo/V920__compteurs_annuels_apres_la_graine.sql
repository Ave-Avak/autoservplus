-- Compteurs annuels des rendez-vous, des interventions et des commandes (V90) recalés sur
-- la graine.
--
-- Sur une base neuve, V90 passe avant la graine : ses compteurs restent vides, puis V900
-- et V915 numérotent leurs documents par les anciennes séquences. Sans ce recalage, la
-- première réservation de la démonstration recevrait RDV-AAAA-0001, déjà pris, et
-- buterait sur uq_rdv_numero. Chaque compteur reprend au plus grand suffixe de son année,
-- sans jamais reculer : sur une base où l'application a déjà numéroté, il garde sa valeur.
-- La graine en volume (V921 et suivantes) tient ensuite les compteurs elle-même.

INSERT INTO compteur_rdv (exercice, dernier_numero)
SELECT substring(numero FROM 5 FOR 4)::SMALLINT, max(substring(numero FROM 10)::INTEGER)
FROM rdv
WHERE numero ~ '^RDV-[0-9]{4}-[0-9]+$'
GROUP BY 1
ON CONFLICT (exercice) DO UPDATE
    SET dernier_numero = greatest(compteur_rdv.dernier_numero, excluded.dernier_numero);

INSERT INTO compteur_intervention (exercice, dernier_numero)
SELECT substring(numero FROM 5 FOR 4)::SMALLINT, max(substring(numero FROM 10)::INTEGER)
FROM intervention
WHERE numero ~ '^INT-[0-9]{4}-[0-9]+$'
GROUP BY 1
ON CONFLICT (exercice) DO UPDATE
    SET dernier_numero = greatest(compteur_intervention.dernier_numero, excluded.dernier_numero);

INSERT INTO compteur_commande (exercice, dernier_numero)
SELECT substring(numero FROM 5 FOR 4)::SMALLINT, max(substring(numero FROM 10)::INTEGER)
FROM commande
WHERE numero ~ '^CMD-[0-9]{4}-[0-9]+$'
GROUP BY 1
ON CONFLICT (exercice) DO UPDATE
    SET dernier_numero = greatest(compteur_commande.dernier_numero, excluded.dernier_numero);
