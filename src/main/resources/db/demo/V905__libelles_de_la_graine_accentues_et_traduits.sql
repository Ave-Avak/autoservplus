-- =====================================================================================
-- Graine de démonstration : accents rétablis, pièces traduites, libellés figés corrigés.
--
-- V900 a écrit ses pièces sans accents (« Filtre a huile ») et recopié sur ses lignes
-- de commande et d'intervention les libellés que V10 et V16 portaient alors. V67
-- corrige le catalogue de référence, cette migration fait de même pour la graine.
--
-- Comme V67, elle ne touche qu'une ligne qui porte encore le texte de la graine.
--
-- Les libellés figés ne sont corrigés que sur les lignes écrites par la graine
-- (created_by = 'demo'), jamais sur une vente réelle : une ligne figée reproduit ce qui
-- a été vendu. Rien ne dépend de ces textes dans le schéma. La facture ne porte aucun
-- libellé et son déclencheur d'immuabilité n'est pas concerné. Sur une base de
-- démonstration où le PDF de la facture de la graine a déjà été téléchargé, le fichier
-- archivé garde l'ancien libellé : un PDF archivé fait foi.
--
-- Sur une base neuve, V67 passe avant V900, qui recopie alors des libellés déjà
-- corrigés pour les prestations : seules les pièces restent à reprendre.
--
-- Traductions écrites par le développeur, à relire par un locuteur.
-- =====================================================================================

UPDATE piece p
SET libelle = v.libelle
FROM (VALUES
          ('FH-1042', 'Filtre a huile', 'Filtre à huile'),
          ('FA-2201', 'Filtre a air',   'Filtre à air')
     ) AS v(reference, graine, libelle)
WHERE p.reference_fabricant = v.reference
  AND p.libelle = v.graine;

UPDATE piece p
SET description = v.description
FROM (VALUES
          ('FH-1042',    'Filtre a huile vissable, moteurs essence et diesel courants.',
                         'Filtre à huile vissable, moteurs essence et diesel courants.'),
          ('FA-2201',    'Filtre a air panneau, remplacement a chaque revision.',
                         'Filtre à air panneau, remplacement à chaque révision.'),
          ('BT-7201',    'Batterie 12 V, 72 Ah, 680 A au demarrage.',
                         'Batterie 12 V, 72 Ah, 680 A au démarrage.'),
          ('HU-5W30-5L', 'Huile synthetique 5W30, bidon de cinq litres.',
                         'Huile synthétique 5W30, bidon de cinq litres.')
     ) AS v(reference, graine, description)
WHERE p.reference_fabricant = v.reference
  AND p.description = v.graine;

UPDATE piece p
SET libelle_nl = v.libelle_nl,
    libelle_en = v.libelle_en,
    description_nl = v.description_nl,
    description_en = v.description_en
FROM (VALUES
          ('FH-1042', 'Filtre à huile', 'Oliefilter', 'Oil filter',
           'Opschroefbare oliefilter voor gangbare benzine- en dieselmotoren.',
           'Spin-on oil filter for common petrol and diesel engines.'),
          ('FA-2201', 'Filtre à air', 'Luchtfilter', 'Air filter',
           'Paneelluchtfilter, te vervangen bij elk onderhoud.',
           'Panel air filter, replaced at each service.'),
          ('PL-3310', 'Jeu de plaquettes avant', 'Set remblokken vooraan', 'Front brake pad set',
           'Set van vier remblokken, montage vooraan.',
           'Set of four pads, front fitting.'),
          ('PN-2055516', 'Pneu 205/55 R16', 'Band 205/55 R16', 'Tyre 205/55 R16',
           'Vierseizoenenband voor personenwagens, draagvermogenindex 91V.',
           'All-season passenger car tyre, load index 91V.'),
          ('BT-7201', 'Batterie 72 Ah', 'Batterij 72 Ah', 'Battery 72 Ah',
           'Batterij 12 V, 72 Ah, startstroom 680 A.',
           '12 V battery, 72 Ah, 680 A starting current.'),
          ('HU-5W30-5L', 'Huile moteur 5W30 (5 L)', 'Motorolie 5W30 (5 L)', 'Engine oil 5W30 (5 L)',
           'Synthetische olie 5W30, bus van vijf liter.',
           'Synthetic 5W30 oil, five-litre can.')
     ) AS v(reference, libelle, libelle_nl, libelle_en, description_nl, description_en)
WHERE p.reference_fabricant = v.reference
  AND p.libelle = v.libelle
  AND p.libelle_nl IS NULL
  AND p.libelle_en IS NULL
  AND p.description_nl IS NULL
  AND p.description_en IS NULL;

-- Libellés figés de la graine. Ses commandes et ses interventions sont en français.
UPDATE ligne_panier l
SET libelle_fige = v.libelle
FROM (VALUES
          ('Filtre a huile', 'Filtre à huile'),
          ('Filtre a air',   'Filtre à air')
     ) AS v(graine, libelle)
WHERE l.created_by = 'demo'
  AND l.libelle_fige = v.graine;

UPDATE ligne_intervention l
SET libelle_fige = v.libelle
FROM (VALUES
          ('Filtre a huile',                  'Filtre à huile'),
          ('Filtre a air',                    'Filtre à air'),
          ('Revision annuelle',               'Révision annuelle'),
          ('Remplacement plaquettes arriere', 'Remplacement plaquettes arrière'),
          ('Montage et equilibrage (1 pneu)', 'Montage et équilibrage (1 pneu)'),
          ('Diagnostic electronique',         'Diagnostic électronique'),
          ('Preparation controle technique',  'Préparation contrôle technique')
     ) AS v(graine, libelle)
WHERE l.created_by = 'demo'
  AND l.libelle_fige = v.graine;
