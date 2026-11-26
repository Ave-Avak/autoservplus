-- Avis de démonstration : douze avis publiés sur cinq prestations de l'accueil.
--
-- Pourquoi : un seul avis (V900) ne montrait ni la note moyenne de l'accueil ni celle des
-- cartes, qui ne s'affichent qu'à partir de trois avis publiés. La répartition le montre
-- dans les deux sens : vidange, révision et plaquettes avant passent le seuil, la recharge
-- de climatisation (deux avis) et le montage d'un pneu (un avis) restent sans note sur leur
-- carte. Notes de 3 à 5, moyenne générale de 4,5 avec l'avis de V900.
--
-- Chaque avis porte sur une intervention terminée de son auteur (règle de dépôt du module,
-- uq_avis_intervention), ouverte au comptoir, sans rendez-vous ni commande, avec sa ligne
-- de prestation et sa chronologie. Douze membres fictifs, en .test, avec une empreinte
-- BCrypt de coût 12 d'un secret tiré au hasard puis jeté : ces comptes ne se connectent
-- pas, seuls ceux du README le font.
--
-- Dates relatives à now(), comme V900 : interventions du mardi au vendredi à 9 h (heure
-- de Bruxelles), de 7 à 23 semaines avant la semaine en cours, avis déposés le lendemain.
-- Aucune facture d'atelier, comme pour l'intervention terminée de V900 : la graine ne
-- compose pas encore de facture de clôture, le volume de démonstration les ajoutera.

DO $$
DECLARE
    v_admin      BIGINT;
    v_lundi      TIMESTAMPTZ := (date_trunc('week', now() AT TIME ZONE 'Europe/Brussels'))
                                AT TIME ZONE 'Europe/Brussels';
    v_exercice   CONSTANT smallint := EXTRACT(YEAR FROM now())::smallint;
    v_duree      INTEGER;
    v_membre     BIGINT;
    v_vehicule   BIGINT;
    v_inter      BIGINT;
    v_debut      TIMESTAMPTZ;
    v_fin        TIMESTAMPTZ;
    a            RECORD;
BEGIN
    SELECT id INTO v_admin FROM utilisateur WHERE email = 'admin@bielle-bleue.test';

    FOR a IN
        SELECT *
          FROM (VALUES
            -- rang, prénom, nom, langue, plaque, marque, modèle, motorisation, année, km,
            -- châssis, prestation, semaines, jour, note, commentaire
            (1,  'Sophie',  'Lemaire',    'fr', '1-BLB-101', 'Peugeot',    '208',     'ESSENCE',    2020, 48200,  'VF3UPHNSKL0000101', 'VIDANGE_STD',       7,  1, 5,
             'Vidange faite à l''heure annoncée, prix identique au devis en ligne.'),
            (2,  'Thomas',  'Peeters',    'nl', '1-BLB-102', 'Toyota',     'Yaris',   'HYBRIDE',    2021, 39500,  'VNKKDBA3X0A000102', 'VIDANGE_STD',       11, 3, 5,
             'Snel geholpen, de wagen stond klaar op het afgesproken uur.'),
            (3,  'Nadia',   'Benali',     'fr', '1-BLB-103', 'Renault',    'Captur',  'ESSENCE',    2019, 71800,  'VF1RJB00062000103', 'VIDANGE_STD',       16, 2, 4,
             'Accueil aimable. Un peu d''attente au comptoir, mais le travail est propre.'),
            (4,  'Lucas',   'Janssens',   'fr', '1-BLB-104', 'Volkswagen', 'Polo',    'ESSENCE',    2018, 92300,  'WVWZZZAWZJU000104', 'REVISION_ANNUELLE', 8,  4, 5,
             'Révision complète, chaque point contrôlé m''a été expliqué à la remise des clés.'),
            (5,  'Élise',   'Moreau',     'fr', '1-BLB-105', 'Citroën',    'C3',      'DIESEL',     2017, 128400, 'VF7SXHMZ6HT000105', 'REVISION_ANNUELLE', 14, 1, 4,
             'Bon travail. J''aurais aimé être prévenue par message quand la voiture était prête.'),
            (6,  'Karim',   'Haddad',     'fr', '1-BLB-106', 'Skoda',      'Octavia', 'DIESEL',     2020, 104900, 'TMBJJ7NE5L0000106', 'REVISION_ANNUELLE', 19, 3, 5,
             'Garage sérieux, aucun travail ajouté sans mon accord.'),
            (7,  'Julie',   'Claes',      'nl', '1-BLB-107', 'Opel',       'Corsa',   'ESSENCE',    2022, 22100,  'W0V7H9EH5N4000107', 'PLAQUETTES_AV',     9,  2, 5,
             'Duidelijke uitleg en eerlijke prijs voor de remblokken.'),
            (8,  'Antoine', 'Dubois',     'fr', '1-BLB-108', 'Ford',       'Focus',   'ESSENCE',    2016, 143600, 'WF0NXXGCHNG000108', 'PLAQUETTES_AV',     13, 4, 4,
             'Plaquettes changées dans la matinée. Le bruit au freinage a disparu.'),
            (9,  'Sarah',   'Van Damme',  'fr', '1-BLB-109', 'Kia',        'Ceed',    'ESSENCE',    2021, 35700,  'U5YH5512AML000109', 'PLAQUETTES_AV',     21, 2, 5,
             'Rendez-vous pris en ligne en deux minutes, voiture rendue à l''heure.'),
            (10, 'Mehdi',   'Amrani',     'fr', '1-BLB-110', 'Dacia',      'Sandero', 'GPL',        2020, 66300,  'UU1B5220564000110', 'RECHARGE_CLIM',     10, 3, 4,
             'Climatisation de nouveau froide. Prix correct.'),
            (11, 'Laura',   'Wouters',    'fr', '1-BLB-111', 'Fiat',       '500',     'ESSENCE',    2019, 54800,  'ZFA3120000J000111', 'RECHARGE_CLIM',     18, 1, 3,
             'Recharge faite, mais il a fallu revenir une seconde fois pour une petite fuite.'),
            (12, 'Hugo',    'Mertens',    'fr', '1-BLB-112', 'BMW',        'Série 1', 'DIESEL',     2018, 118700, 'WBA1S510605000112', 'MONTAGE_PNEU',      23, 4, 5,
             'Pneus montés et équilibrés rapidement, atelier très propre.')
          ) AS t(rang, prenom, nom, langue, plaque, marque, modele, motorisation, annee, km,
                 chassis, code, semaines, jour, note, commentaire)
         ORDER BY rang
    LOOP
        v_debut := v_lundi - make_interval(weeks => a.semaines) + make_interval(days => a.jour, hours => 9);

        INSERT INTO utilisateur (type_utilisateur, email, mot_de_passe_hache, nom, prenom,
                                 langue, statut, email_verifie, created_by, updated_by)
        VALUES ('MEMBRE',
                lower(translate(a.prenom, 'ÉéèëïöüÀ', 'eeeeiouA')) || '.'
                    || lower(replace(a.nom, ' ', '-')) || '@demo.test',
                '$2a$12$961JgZHdN4jNeOMupQP3Muk9JMKquj8RTs1e.xP8X8R8V5vjah3US',
                a.nom, a.prenom, a.langue, 'ACTIF', TRUE, 'demo', 'demo')
        RETURNING id INTO v_membre;

        INSERT INTO vehicule (membre_id, plaque, marque, modele, motorisation, annee,
                              kilometrage, numero_chassis, actif, created_by, updated_by)
        VALUES (v_membre, a.plaque, a.marque, a.modele, a.motorisation, a.annee, a.km,
                a.chassis, TRUE, 'demo', 'demo')
        RETURNING id INTO v_vehicule;

        SELECT duree_minutes INTO STRICT v_duree FROM service WHERE code = a.code;
        v_fin := v_debut + make_interval(mins => v_duree + 15);

        INSERT INTO intervention (numero, vehicule_id, statut, montant_devis_htva, debut_reel,
                                  fin_reelle, kilometrage_releve, created_by, updated_by)
        SELECT 'INT-' || v_exercice || '-' || lpad(nextval('seq_numero_intervention')::text, 4, '0'),
               v_vehicule, 'TERMINEE', prix_htva, v_debut, v_fin, a.km, 'demo', 'demo'
          FROM service WHERE code = a.code
        RETURNING id INTO v_inter;

        INSERT INTO ligne_intervention (intervention_id, service_id, libelle_fige, quantite,
                                        prix_unitaire_htva, taux_tva, created_by, updated_by)
        SELECT v_inter, id, libelle, 1, prix_htva, taux_tva, 'demo', 'demo'
          FROM service WHERE code = a.code;

        INSERT INTO historique_statut_intervention (intervention_id, statut_avant, statut_apres,
                                                    horodatage, auteur_id, motif, created_by, updated_by)
        VALUES (v_inter, NULL, 'PLANIFIEE', v_debut - interval '10 minutes', v_admin, NULL, 'demo', 'demo'),
               (v_inter, 'PLANIFIEE', 'EN_COURS', v_debut, v_admin, NULL, 'demo', 'demo'),
               (v_inter, 'EN_COURS', 'TERMINEE', v_fin, v_admin, NULL, 'demo', 'demo');

        INSERT INTO avis (membre_id, intervention_id, note, commentaire, publie, signale,
                          date_depot, created_by, updated_by)
        VALUES (v_membre, v_inter, a.note, a.commentaire, TRUE, FALSE,
                v_fin + interval '1 day', 'demo', 'demo');
    END LOOP;
END $$;
