-- Donnees de demonstration : catalogue de prestations pour rendre la
-- reservation utilisable et alimenter la demo. Prix et durees provisoires,
-- a calibrer avant la mise en production reelle. Ne pas considerer comme
-- une grille tarifaire definitive.
--
-- Les categories referencees existent depuis V10. taux_tva laisse au defaut
-- (21 %, TVA belge standard sur services garage). Durees multiples de 30 min
-- pour s aligner sur le pas atelier par defaut.

INSERT INTO service (categorie_id, code, libelle, prix_htva, duree_minutes)
VALUES
    ((SELECT id FROM categorie WHERE code = 'ENTRETIEN'),   'VIDANGE_STD',        'Vidange standard',                    65.00,  30),
    ((SELECT id FROM categorie WHERE code = 'ENTRETIEN'),   'REVISION_ANNUELLE',  'Revision annuelle',                  180.00,  90),
    ((SELECT id FROM categorie WHERE code = 'FREINAGE'),    'PLAQUETTES_AV',      'Remplacement plaquettes avant',      120.00,  60),
    ((SELECT id FROM categorie WHERE code = 'FREINAGE'),    'PLAQUETTES_AR',      'Remplacement plaquettes arriere',    100.00,  60),
    ((SELECT id FROM categorie WHERE code = 'PNEUMATIQUE'), 'MONTAGE_PNEU',       'Montage et equilibrage (1 pneu)',     25.00,  30),
    ((SELECT id FROM categorie WHERE code = 'PNEUMATIQUE'), 'PERMUTATION_PNEUS',  'Permutation des 4 pneus',             40.00,  30),
    ((SELECT id FROM categorie WHERE code = 'DIAGNOSTIC'),  'DIAG_ELEC',          'Diagnostic electronique',             55.00,  30),
    ((SELECT id FROM categorie WHERE code = 'CARROSSERIE'), 'RETOUCHE_PEINTURE',  'Retouche peinture',                   90.00,  60),
    ((SELECT id FROM categorie WHERE code = 'CT'),          'PREPARATION_CT',     'Preparation controle technique',      45.00,  30);
