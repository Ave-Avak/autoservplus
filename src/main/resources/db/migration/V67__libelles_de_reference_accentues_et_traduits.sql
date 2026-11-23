-- =====================================================================================
-- Libellés de référence : accents rétablis, traductions néerlandaises et anglaises.
--
-- V10 (catégories, compte administrateur), V16 (prestations) et V17 (postes d'atelier)
-- ont été écrites sans accents : « Revision annuelle », « Batteries et electricite »,
-- « Systeme », « Pont elevateur principal ». Une
-- migration appliquée ne se modifie pas, celle-ci corrige donc les lignes.
--
-- Une ligne n'est touchée que si elle porte encore le texte de sa graine. Un garage qui
-- a renommé une prestation au back-office garde son libellé, et ne reçoit pas non plus
-- la traduction d'un nom qui n'est plus le sien. Une traduction déjà saisie reste en
-- place : les deux colonnes ne sont remplies que si elles sont vides toutes les deux.
--
-- Les libellés figés des lignes de commande et d'intervention ne sont pas retouchés
-- ici : ils reproduisent ce qui a été vendu sous ce nom. Seule la graine de
-- démonstration corrige les siens, dans db/demo.
--
-- Traductions écrites par le développeur, à relire par un locuteur.
-- =====================================================================================

-- Catégories de V10 : libellé et description en français.
UPDATE categorie c
SET libelle = v.libelle
FROM (VALUES
          ('DIAGNOSTIC', 'Diagnostic electronique',        'Diagnostic électronique'),
          ('CT',         'Preparation controle technique', 'Préparation contrôle technique'),
          ('P_FREINAGE', 'Pieces de freinage',             'Pièces de freinage'),
          ('P_BATTERIE', 'Batteries et electricite',       'Batteries et électricité')
     ) AS v(code, graine, libelle)
WHERE c.code = v.code
  AND c.libelle = v.graine;

UPDATE categorie c
SET description = v.description
FROM (VALUES
          ('ENTRETIEN',   'Vidange, filtres, revision periodique',      'Vidange, filtres, révision périodique'),
          ('PNEUMATIQUE', 'Montage, equilibrage, permutation',          'Montage, équilibrage, permutation'),
          ('DIAGNOSTIC',  'Lecture de codes defaut, controle capteurs', 'Lecture de codes défaut, contrôle capteurs'),
          ('CARROSSERIE', 'Petites reparations et retouches',           'Petites réparations et retouches'),
          ('CT',          'Verification avant passage au controle',     'Vérification avant passage au contrôle'),
          ('P_FREINAGE',  'Plaquettes, disques, etriers',               'Plaquettes, disques, étriers'),
          ('P_PNEUS',     'Ete, hiver, quatre saisons',                 'Été, hiver, quatre saisons'),
          ('P_HUILE',     'Huiles moteur et boite',                     'Huiles moteur et boîte')
     ) AS v(code, graine, description)
WHERE c.code = v.code
  AND c.description = v.graine;

UPDATE categorie c
SET libelle_nl = v.neerlandais,
    libelle_en = v.anglais
FROM (VALUES
          ('ENTRETIEN',   'Entretien courant',              'Periodiek onderhoud',            'Routine maintenance'),
          ('FREINAGE',    'Freinage',                       'Remmen',                         'Brakes'),
          ('PNEUMATIQUE', 'Pneumatique',                    'Bandenservice',                  'Tyre fitting'),
          ('DIAGNOSTIC',  'Diagnostic électronique',        'Elektronische diagnose',         'Electronic diagnostics'),
          ('CARROSSERIE', 'Carrosserie',                    'Carrosserie',                    'Bodywork'),
          ('CT',          'Préparation contrôle technique', 'Voorbereiding autokeuring',      'Technical inspection preparation'),
          ('P_FILTRES',   'Filtres',                        'Filters',                        'Filters'),
          ('P_FREINAGE',  'Pièces de freinage',             'Remonderdelen',                  'Brake parts'),
          ('P_PNEUS',     'Pneumatiques',                   'Banden',                         'Tyres'),
          ('P_BATTERIE',  'Batteries et électricité',       'Batterijen en elektriciteit',    'Batteries and electrics'),
          ('P_HUILE',     'Lubrifiants',                    'Smeermiddelen',                  'Lubricants')
     ) AS v(code, libelle, neerlandais, anglais)
WHERE c.code = v.code
  AND c.libelle = v.libelle
  AND c.libelle_nl IS NULL
  AND c.libelle_en IS NULL;

-- Prestations de V16. Le back-office refuse deux prestations du même nom (A1) : un
-- libellé n'est pas corrigé si une autre prestation porte déjà le nom corrigé.
UPDATE service s
SET libelle = v.libelle
FROM (VALUES
          ('REVISION_ANNUELLE', 'Revision annuelle',               'Révision annuelle'),
          ('PLAQUETTES_AR',     'Remplacement plaquettes arriere', 'Remplacement plaquettes arrière'),
          ('MONTAGE_PNEU',      'Montage et equilibrage (1 pneu)', 'Montage et équilibrage (1 pneu)'),
          ('DIAG_ELEC',         'Diagnostic electronique',         'Diagnostic électronique'),
          ('PREPARATION_CT',    'Preparation controle technique',  'Préparation contrôle technique')
     ) AS v(code, graine, libelle)
WHERE s.code = v.code
  AND s.libelle = v.graine
  AND NOT EXISTS (SELECT 1 FROM service autre
                  WHERE autre.id <> s.id AND lower(autre.libelle) = lower(v.libelle));

UPDATE service s
SET libelle_nl = v.neerlandais,
    libelle_en = v.anglais
FROM (VALUES
          ('VIDANGE_STD',       'Vidange standard',                'Standaard olieverversing',         'Standard oil change'),
          ('REVISION_ANNUELLE', 'Révision annuelle',               'Jaarlijks onderhoud',              'Annual service'),
          ('PLAQUETTES_AV',     'Remplacement plaquettes avant',   'Vervanging remblokken vooraan',    'Front brake pad replacement'),
          ('PLAQUETTES_AR',     'Remplacement plaquettes arrière', 'Vervanging remblokken achteraan',  'Rear brake pad replacement'),
          ('MONTAGE_PNEU',      'Montage et équilibrage (1 pneu)', 'Montage en balancering (1 band)',  'Fitting and balancing (1 tyre)'),
          ('PERMUTATION_PNEUS', 'Permutation des 4 pneus',         'Wisselen van de 4 banden',         'Rotation of the 4 tyres'),
          ('DIAG_ELEC',         'Diagnostic électronique',         'Elektronische diagnose',           'Electronic diagnostics'),
          ('RETOUCHE_PEINTURE', 'Retouche peinture',               'Lakherstelling',                   'Paint touch-up'),
          ('PREPARATION_CT',    'Préparation contrôle technique',  'Voorbereiding autokeuring',        'Technical inspection preparation')
     ) AS v(code, libelle, neerlandais, anglais)
WHERE s.code = v.code
  AND s.libelle = v.libelle
  AND s.libelle_nl IS NULL
  AND s.libelle_en IS NULL;

-- Postes d'atelier de V17 : seule la description manquait d'accents. Les postes ne sont
-- pas traduits : aucun écran ne les montre au membre.
UPDATE poste_atelier p
SET description = v.description
FROM (VALUES
          ('Pont 1',          'Pont elevateur principal',  'Pont élévateur principal'),
          ('Pont 2',          'Pont elevateur secondaire', 'Pont élévateur secondaire'),
          ('Baie diagnostic', 'Baie sans elevation pour diagnostic et pneumatique',
                              'Baie sans élévation pour diagnostic et pneumatique')
     ) AS v(libelle, graine, description)
WHERE p.libelle = v.libelle
  AND p.description = v.graine;

-- Compte administrateur de V10 : « Système Administrateur », fonction « Gérant ». Un
-- compte renommé depuis garde son nom, une fonction changée reste telle quelle.
UPDATE utilisateur
SET prenom = 'Système'
WHERE email = 'admin@autoservplus.be'
  AND prenom = 'Systeme'
  AND nom = 'Administrateur';

UPDATE utilisateur
SET fonction = 'Gérant'
WHERE email = 'admin@autoservplus.be'
  AND fonction = 'Gerant';
