-- =====================================================================================
-- Zone 10 : donnees de reference
-- Minimum necessaire au demarrage de l application. Aucune donnee de demonstration.
-- =====================================================================================

INSERT INTO categorie (code, libelle, type, description, ordre) VALUES
                                                                    ('ENTRETIEN',    'Entretien courant',             'SERVICE', 'Vidange, filtres, revision periodique',       1),
                                                                    ('FREINAGE',     'Freinage',                      'SERVICE', 'Plaquettes, disques, liquide de frein',       2),
                                                                    ('PNEUMATIQUE',  'Pneumatique',                   'SERVICE', 'Montage, equilibrage, permutation',           3),
                                                                    ('DIAGNOSTIC',   'Diagnostic electronique',       'SERVICE', 'Lecture de codes defaut, controle capteurs',  4),
                                                                    ('CARROSSERIE',  'Carrosserie',                   'SERVICE', 'Petites reparations et retouches',            5),
                                                                    ('CT',           'Preparation controle technique','SERVICE', 'Verification avant passage au controle',      6),
                                                                    ('P_FILTRES',    'Filtres',                       'PIECE',   'Huile, air, habitacle, carburant',            7),
                                                                    ('P_FREINAGE',   'Pieces de freinage',            'PIECE',   'Plaquettes, disques, etriers',                8),
                                                                    ('P_PNEUS',      'Pneumatiques',                  'PIECE',   'Ete, hiver, quatre saisons',                  9),
                                                                    ('P_BATTERIE',   'Batteries et electricite',      'PIECE',   'Batteries, bougies, ampoules',               10),
                                                                    ('P_HUILE',      'Lubrifiants',                   'PIECE',   'Huiles moteur et boite',                     11);

-- Ouverture du lundi au vendredi, plus le samedi matin.
INSERT INTO plage_ouverture (jour_semaine, heure_debut, heure_fin) VALUES
                                                                       (1, '08:00', '12:00'), (1, '13:00', '18:00'),
                                                                       (2, '08:00', '12:00'), (2, '13:00', '18:00'),
                                                                       (3, '08:00', '12:00'), (3, '13:00', '18:00'),
                                                                       (4, '08:00', '12:00'), (4, '13:00', '18:00'),
                                                                       (5, '08:00', '12:00'), (5, '13:00', '17:00'),
                                                                       (6, '09:00', '13:00');

-- Compte administrateur initial.
-- Empreinte BCrypt facteur 12 de la chaine "ChangezMoi2026!".
-- Ce mot de passe doit imperativement etre change a la premiere connexion.
INSERT INTO utilisateur (
    type_utilisateur, email, mot_de_passe_hache, nom, prenom,
    langue, statut, email_verifie, fonction, created_by, updated_by
) VALUES (
             'ADMINISTRATEUR', 'admin@autoservplus.be',
             '$2a$12$8K1p/a0dURXAm7QiTRqTOuKzKe7BJ9YlKz5CoFDoOB0jQZmLGxKPe',
             'Administrateur', 'Systeme',
             'fr', 'ACTIF', TRUE, 'Gerant', 'migration', 'migration'
         );

INSERT INTO place_parking (numero, type, tarif_jour_htva) VALUES
                                                              ('P01', 'STANDARD',  8.00), ('P02', 'STANDARD',  8.00),
                                                              ('P03', 'STANDARD',  8.00), ('P04', 'STANDARD',  8.00),
                                                              ('P05', 'COUVERTE', 12.00), ('P06', 'COUVERTE', 12.00),
                                                              ('P07', 'GRANDE',   14.00), ('P08', 'PMR',       8.00);