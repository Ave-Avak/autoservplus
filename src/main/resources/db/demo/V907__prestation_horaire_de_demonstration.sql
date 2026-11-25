-- =====================================================================================
-- Prestation horaire de démonstration : la main-d'œuvre au taux du garage (Q-H-39).
--
-- Le schéma de production n'insère aucun prix : le garage crée sa prestation à l'heure au
-- back-office (V72). La démonstration en porte une, à 65,00 € HTVA de l'heure, pour que
-- l'atelier puisse ajouter des heures à une intervention et que la fiche publique montre
-- un taux horaire. Une heure de durée, celle d'une unité (ck_service_duree_heure).
--
-- Jouée après la graine, à la suite de V905 et V906 : elle n'insère rien dont V900 dépende.
-- Une base qui porterait déjà ce code n'est pas touchée.
-- =====================================================================================

INSERT INTO service (categorie_id, code, libelle, libelle_nl, libelle_en, description, description_nl,
                     description_en, prix_htva, taux_tva, duree_minutes, unite, created_by, updated_by)
SELECT c.id, 'MAIN_OEUVRE_HEURE', 'Main-d''œuvre (heure)', 'Arbeidsloon (per uur)', 'Labour (per hour)',
       'Temps de travail de l''atelier, facturé à l''heure selon le temps passé.',
       'Werktijd van de werkplaats, per uur aangerekend volgens de bestede tijd.',
       'Workshop labour, charged by the hour according to the time spent.',
       65.00, 21.00, 60, 'HEURE', 'demo', 'demo'
FROM categorie c
WHERE c.code = 'ENTRETIEN'
  AND NOT EXISTS (SELECT 1 FROM service WHERE code = 'MAIN_OEUVRE_HEURE');
