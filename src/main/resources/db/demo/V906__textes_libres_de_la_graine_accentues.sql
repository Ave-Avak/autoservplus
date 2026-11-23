-- =====================================================================================
-- Graine de démonstration : accents et apostrophes rétablis dans les textes libres.
--
-- V900 a écrit sans accents ni apostrophes les commentaires des rendez-vous, un motif de
-- refus, les commentaires de l'atelier, deux motifs de l'historique d'une intervention,
-- un avis, deux messages et trois titres de notification (« Rendez-vous confirme »,
-- « sous la cote d usure minimale »). V905 a repris les pièces et les libellés figés,
-- cette migration reprend le reste.
--
-- Comme V905, elle ne touche qu'une ligne écrite par la graine (created_by = 'demo') qui
-- porte encore exactement son texte d'origine. Un texte repris depuis par le garage ou
-- par le membre reste tel qu'il l'a écrit. Aucune de ces tables ne porte de déclencheur
-- d'immuabilité, et aucun document archivé ne reprend ces textes.
-- =====================================================================================

UPDATE rdv r
SET commentaire = v.corrige
FROM (VALUES
          ('Bruit de freinage a l avant depuis une semaine.', 'Bruit de freinage à l''avant depuis une semaine.'),
          ('Revision annuelle.',                              'Révision annuelle.'),
          ('Diagnostic electronique.',                        'Diagnostic électronique.'),
          ('Montage d un pneu.',                              'Montage d''un pneu.')
     ) AS v(graine, corrige)
WHERE r.created_by = 'demo'
  AND r.commentaire = v.graine;

UPDATE rdv
SET motif_refus = 'Atelier fermé ce jour pour formation du personnel.'
WHERE created_by = 'demo'
  AND motif_refus = 'Atelier ferme ce jour pour formation du personnel.';

UPDATE intervention i
SET commentaire_admin = v.corrige
FROM (VALUES
          ('Batterie non disponible en stock, reapprovisionnement attendu.',
           'Batterie non disponible en stock, réapprovisionnement attendu.'),
          ('Le client a fait realiser le diagnostic ailleurs.',
           'Le client a fait réaliser le diagnostic ailleurs.')
     ) AS v(graine, corrige)
WHERE i.created_by = 'demo'
  AND i.commentaire_admin = v.graine;

UPDATE historique_statut_intervention h
SET motif = v.corrige
FROM (VALUES
          ('Depassement du devis : plaquettes a remplacer.', 'Dépassement du devis : plaquettes à remplacer.'),
          ('Diagnostic realise ailleurs.',                   'Diagnostic réalisé ailleurs.')
     ) AS v(graine, corrige)
WHERE h.created_by = 'demo'
  AND h.motif = v.graine;

UPDATE avis
SET commentaire = 'Révision faite dans les temps, explications claires sur les points contrôlés.'
WHERE created_by = 'demo'
  AND commentaire = 'Revision faite dans les temps, explications claires sur les points controles.';

UPDATE message m
SET corps = v.corrige
FROM (VALUES
          ('Bonjour, les plaquettes sont-elles vraiment a remplacer maintenant ?',
           'Bonjour, les plaquettes sont-elles vraiment à remplacer maintenant ?'),
          ('Bonjour, elles sont sous la cote d usure minimale. Le devis est en attente de votre accord.',
           'Bonjour, elles sont sous la cote d''usure minimale. Le devis est en attente de votre accord.')
     ) AS v(graine, corrige)
WHERE m.created_by = 'demo'
  AND m.corps = v.graine;

UPDATE notification n
SET titre = v.corrige
FROM (VALUES
          ('Rendez-vous confirme',  'Rendez-vous confirmé'),
          ('Intervention terminee', 'Intervention terminée'),
          ('Commande payee',        'Commande payée')
     ) AS v(graine, corrige)
WHERE n.created_by = 'demo'
  AND n.titre = v.graine;
