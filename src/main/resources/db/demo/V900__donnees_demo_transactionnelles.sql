-- =====================================================================================
-- Jeu de donnees de DEMONSTRATION transactionnel.
--
-- But : un evaluateur doit pouvoir parcourir la totalite de l application sans rien
-- creer lui-meme. V10 et V16/V17 semaient deja les donnees de REFERENCE (categories,
-- prestations, postes, plages d ouverture) ; il manquait tout ce qui se passe ENSUITE
-- — comptes, vehicules, rendez-vous, dossiers d atelier, vente, facture, avis,
-- messagerie. Sans cela, chaque ecran de suivi s ouvrait sur un tableau vide, et la
-- moitie des parcours n etait tout simplement pas atteignable.
--
-- ------------------------------------------------------------------------------------
-- POURQUOI 900, ET NON LE NUMERO SUIVANT
-- ------------------------------------------------------------------------------------
-- Cette graine vit dans db/demo, chargee sous le seul profil « demo ». Une base de
-- production n a donc AUCUN moyen de la resoudre — et c est voulu. Mais le dump publie
-- pour l evaluation est pris AVEC ce profil : son flyway_schema_history porte cette
-- ligne. Une base restauree depuis ce dump connait donc une migration appliquee que le
-- classpath ne resout pas.
--
-- Flyway traite ce cas de deux facons opposees selon le NUMERO :
--
--   - version SUPERIEURE a toutes les migrations resolues -> « future », toleree,
--     simple avertissement au demarrage ;
--   - version INFERIEURE a l une d elles                  -> « missing », erreur
--     fatale : « Detected applied migration not resolved locally ».
--
-- Numerotee 34, cette graine n etait « future » que tant qu aucune migration de schema
-- ne la depassait. La premiere qui l a fait — V35 — a suffi a rendre le dump
-- indemarrable, alors que rien du jeu de donnees n avait change. Le defaut etait
-- structurel et n attendait qu une migration de plus, quelle qu elle soit.
--
-- 900 place la graine hors d atteinte de la plage des migrations de schema : elle reste
-- « future » pour toute base qui l a appliquee sans pouvoir la resoudre, quel que soit
-- le nombre de migrations ajoutees ensuite. Le numero 34 reste libre et inutilise ; un
-- trou dans la numerotation n a aucun effet pour Flyway, qui ordonne sans exiger de
-- continuite.
--
-- Effet de bord voulu : sous le profil « demo », la graine s applique desormais APRES
-- toutes les migrations de schema. C est le bon ordre — semer des donnees au milieu
-- d une evolution de schema les expose a etre reprises par les migrations suivantes.
--
-- Aucun garde-fou n est desactive au passage : « missing » continue de faire echouer
-- la validation, ce qui reste souhaitable pour une migration de schema reellement
-- supprimee.
--
-- ------------------------------------------------------------------------------------
-- CE QUI EST DU DML DE DEMONSTRATION, PAS DU SCHEMA
-- ------------------------------------------------------------------------------------
-- Aucune table, aucune colonne, aucune contrainte : cette migration n ajoute que des
-- lignes, comme V10, V16 et V17. Le compte de tables metier reste a 34.
--
-- ------------------------------------------------------------------------------------
-- DATES RELATIVES, ET POURQUOI
-- ------------------------------------------------------------------------------------
-- Tout est calcule depuis now() a l execution, jamais ecrit en dur. Un jeu de demo date
-- se perime : des rendez-vous « a venir » passes dans le passe cessent d apparaitre aux
-- ecrans qui filtrent sur l horizon, et la demonstration se vide d elle-meme au bout de
-- quelques semaines. Ici, la base rejouee dans six mois produit le meme scenario.
--
-- Les rendez-vous a venir sont poses a partir du lundi de la semaine +2 : entre sept et
-- quatorze jours d ici, donc toujours au-dela du delai minimal de 24 h et en deca de
-- l horizon de 60 jours (parametre_atelier), quel que soit le jour ou la migration
-- s execute. Les heures suivent les plages d ouverture reelles (08:00-12:00 et
-- 13:00-18:00 en semaine), exprimees en Europe/Brussels puis converties : un rendez-vous
-- affiche a 9 h doit etre a 9 h pour le garage, pas a 9 h UTC.
--
-- ------------------------------------------------------------------------------------
-- CONTRAINTES RESPECTEES, ET COMMENT
-- ------------------------------------------------------------------------------------
-- ex_rdv_poste_intervalle (GiST) interdit deux rendez-vous qui se chevauchent sur un
-- meme poste, mais UNIQUEMENT pour les statuts EN_ATTENTE et CONFIRME. Les rendez-vous
-- passes (HONORE, ABSENT, ANNULE, REFUSE) en sont exclus par la clause WHERE de la
-- contrainte : ils peuvent donc partager des creneaux sans la violer. Seuls les deux
-- rendez-vous a venir sont places sur des intervalles disjoints.
--
-- La numerotation des factures doit rester une suite SANS TROU par exercice : la ligne
-- inseree ici est la premiere de l exercice, et compteur_facture est mis a jour en
-- consequence. Sans cette mise a jour, la premiere facture emise par l application
-- reprendrait a 1 et violerait uq_facture_numero.
--
-- Les numeros de rendez-vous, d intervention et de commande sont tires des memes
-- sequences que celles employees par l application (nextval), et non ecrits en dur :
-- c est ce qui garantit qu une creation faite depuis l interface, apres restauration,
-- ne collisionne pas avec le jeu de demo.
--
-- ------------------------------------------------------------------------------------
-- AUCUNE DONNEE PERSONNELLE REELLE
-- ------------------------------------------------------------------------------------
-- Les adresses de courriel sont dans le domaine de premier niveau .test, reserve par la
-- RFC 2606 et non routable : meme si une implementation d envoi reelle etait branchee
-- par erreur sur ce jeu, aucun message ne pourrait atteindre une boite existante. Les
-- noms, plaques, numeros de chassis et adresses sont inventes.
--
-- Les empreintes de mot de passe sont de vraies empreintes BCrypt de cout 12, produites
-- par le meme encodeur que l application. Les mots de passe en clair sont documentes en
-- tete du dump (docs/dump_autoservplus.sql) : c est acceptable pour un jeu de
-- demonstration, et a bannir en production au meme titre que le compte admin de V10.
-- =====================================================================================

DO
$$
    DECLARE
        -- Ancrage temporel unique : toutes les dates en derivent, de sorte que le
        -- scenario reste coherent meme si l execution dure plusieurs secondes.
        v_zone            CONSTANT text        := 'Europe/Brussels';
        v_maintenant      CONSTANT timestamptz := now();
        -- Lundi de la semaine +2, a minuit heure locale.
        v_lundi           CONSTANT timestamptz :=
            (date_trunc('week', (v_maintenant AT TIME ZONE v_zone)) + interval '14 days')
                AT TIME ZONE v_zone;
        v_exercice        CONSTANT smallint    := EXTRACT(YEAR FROM v_maintenant)::smallint;

        v_membre          bigint;
        v_admin           bigint;
        v_golf            bigint;
        v_clio            bigint;
        v_poste1          bigint;
        v_poste2          bigint;

        v_rdv_confirme    bigint;
        v_rdv_honore      bigint;

        v_int_terminee    bigint;
        v_int_planifiee   bigint;
        v_int_en_cours    bigint;
        v_int_suspendue   bigint;
        v_int_validation  bigint;
        v_int_annulee     bigint;

        v_panier          bigint;
        v_commande        bigint;
        v_facture         bigint;
        v_conversation    bigint;

        v_filtre          bigint;
        v_plaquettes      bigint;
        v_batterie        bigint;

        v_version_cgv     varchar(20);
        v_version_cookies varchar(20);

        v_montant_htva    numeric(10, 2);
        v_montant_tva     numeric(10, 2);
    BEGIN
        SELECT id INTO v_admin FROM utilisateur WHERE email = 'admin@autoservplus.be';
        SELECT id INTO v_poste1 FROM poste_atelier ORDER BY ordre, id LIMIT 1;
        SELECT id INTO v_poste2 FROM poste_atelier ORDER BY ordre, id OFFSET 1 LIMIT 1;

        -- CGV : version ECRITE EN DUR, et c est un choix de scenario, pas un oubli.
        --
        -- Depuis que cette graine porte le numero 900, elle s execute APRES toutes les
        -- migrations de schema, donc apres V35 qui publie CGV-2026-02. Deriver la
        -- version active donnerait desormais 2026-02, et le jeu de demonstration
        -- montrerait un membre ayant accepte le texte du jour — le cas le moins
        -- interessant, celui ou le versionnage ne se voit pas.
        --
        -- 2026-01 est retenue pour que la demonstration porte le cas que F24 sert a
        -- traiter : un membre dont la preuve designe une redaction REMPLACEE, dont le
        -- texte reste consultable a /documents/cgv/CGV-2026-01 alors que /cgv affiche
        -- deja la suivante. Une preuve doit dire QUOI a ete accepte, pas seulement QUE
        -- quelque chose l a ete ; on ne le montre qu avec deux versions.
        --
        -- Ecrire un identifiant en dur ICI ne reproduit pas le defaut d avant F24 :
        -- ce n est pas l application qui enregistre un consentement, c est un jeu de
        -- donnees qui choisit ce que le membre fictif a accepte. La contrainte reste
        -- entiere — la valeur doit designer une ligne reelle de version_document, et
        -- le SELECT ci-dessous echoue bruyamment si elle n existe pas, plutot que de
        -- laisser passer une preuve orpheline.
        SELECT version INTO STRICT v_version_cgv
        FROM version_document
        WHERE type_document = 'CGV' AND version = 'CGV-2026-01' AND langue = 'fr';

        -- COOKIES : derivee, elle. Ce document n a qu une version, et rien dans ce jeu
        -- ne demande d en illustrer le remplacement — la regle generale s applique donc.
        SELECT version INTO v_version_cookies
        FROM version_document
        WHERE type_document = 'COOKIES' AND langue = 'fr' AND actif
        ORDER BY date_effet DESC, id DESC
        LIMIT 1;

        -- =============================================================================
        -- 1. Pieces detachees
        --
        -- Le catalogue livre par V16 ne contient QUE des prestations : la table piece
        -- etait vide, donc l ecran public /pieces, le panier, la commande et toute la
        -- chaine marchande n avaient aucune matiere. Les cinq categories de pieces
        -- existaient pourtant depuis V10, sans une seule ligne dedans.
        --
        -- Stocks volontairement contrastes : une reference sous son seuil d alerte, pour
        -- que l indicateur de reapprovisionnement du tableau de bord ait quelque chose a
        -- montrer plutot que d afficher zero.
        -- =============================================================================
        INSERT INTO piece (categorie_id, reference_fabricant, libelle, description, marque,
                           prix_htva, taux_tva, quantite_stock, seuil_alerte, actif,
                           created_by, updated_by)
        SELECT c.id, v.ref, v.libelle, v.description, v.marque,
               v.prix, 21.00, v.stock, v.seuil, TRUE, 'demo', 'demo'
        FROM (VALUES
                  ('P_FILTRES', 'FH-1042', 'Filtre a huile', 'Filtre a huile vissable, moteurs essence et diesel courants.', 'Mann', 12.40, 40, 10),
                  ('P_FILTRES', 'FA-2201', 'Filtre a air', 'Filtre a air panneau, remplacement a chaque revision.', 'Bosch', 18.90, 25, 8),
                  ('P_FREINAGE', 'PL-3310', 'Jeu de plaquettes avant', 'Jeu de quatre plaquettes, montage avant.', 'Ferodo', 46.50, 12, 4),
                  ('P_PNEUS', 'PN-2055516', 'Pneu 205/55 R16', 'Pneu tourisme quatre saisons, indice de charge 91V.', 'Michelin', 89.00, 16, 8),
                  ('P_BATTERIE', 'BT-7201', 'Batterie 72 Ah', 'Batterie 12 V, 72 Ah, 680 A au demarrage.', 'Varta', 118.00, 3, 5),
                  ('P_HUILE', 'HU-5W30-5L', 'Huile moteur 5W30 (5 L)', 'Huile synthetique 5W30, bidon de cinq litres.', 'Total', 42.00, 30, 10)
             ) AS v(cat, ref, libelle, description, marque, prix, stock, seuil)
                 JOIN categorie c ON c.code = v.cat;

        SELECT id INTO v_filtre FROM piece WHERE reference_fabricant = 'FH-1042';
        SELECT id INTO v_plaquettes FROM piece WHERE reference_fabricant = 'PL-3310';
        SELECT id INTO v_batterie FROM piece WHERE reference_fabricant = 'BT-7201';

        -- =============================================================================
        -- 2. Compte membre
        --
        -- email_verifie a TRUE et statut ACTIF : le compte doit etre immediatement
        -- utilisable. Un compte laisse EN_ATTENTE_VALIDATION obligerait l evaluateur a
        -- retrouver un jeton de verification dans les journaux avant de pouvoir se
        -- connecter — exactement l obstacle que ce jeu doit supprimer.
        -- =============================================================================
        INSERT INTO utilisateur (type_utilisateur, email, mot_de_passe_hache, nom, prenom,
                                 telephone, rue, numero_rue, code_postal, localite, pays,
                                 langue, statut, email_verifie, created_by, updated_by)
        VALUES ('MEMBRE', 'marie.dupont@demo.test',
                '$2a$12$o7vMe8EtQXWUxsZzGpLTQ.5xkKUW71RmSkEioCAOWoGBgG6kQ5NdS',
                'Dupont', 'Marie', '+32 470 00 00 01',
                'Rue des Ateliers', '18', '1000', 'Bruxelles', 'Belgique',
                'fr', 'ACTIF', TRUE, 'demo', 'demo')
        RETURNING id INTO v_membre;

        -- =============================================================================
        -- 3. Parc de vehicules
        -- =============================================================================
        INSERT INTO vehicule (membre_id, plaque, marque, modele, motorisation, annee,
                              kilometrage, numero_chassis, actif, created_by, updated_by)
        VALUES (v_membre, '1-DEM-001', 'Volkswagen', 'Golf', 'DIESEL', 2019, 96500,
                'WVWZZZ1KZAW000001', TRUE, 'demo', 'demo')
        RETURNING id INTO v_golf;

        INSERT INTO vehicule (membre_id, plaque, marque, modele, motorisation, annee,
                              kilometrage, numero_chassis, actif, created_by, updated_by)
        VALUES (v_membre, '1-DEM-002', 'Renault', 'Clio', 'ESSENCE', 2022, 31200,
                'VF1RJA00000000002', TRUE, 'demo', 'demo')
        RETURNING id INTO v_clio;

        -- =============================================================================
        -- 4. Preuves de consentement
        --
        -- La version acceptee est LUE dans version_document (F24). Le refus du marketing
        -- est ecrit au meme titre que l acceptation de la mesure d audience : une absence
        -- de ligne serait ambigue entre « a refuse » et « n a jamais ete interroge ».
        -- Aucune ligne pour les cookies necessaires, exemptes de consentement.
        -- =============================================================================
        INSERT INTO consentement (utilisateur_id, type_document, version_acceptee, accorde,
                                  date_consentement, adresse_ip)
        VALUES (v_membre, 'CGV', v_version_cgv, TRUE, v_maintenant - interval '40 days', '198.51.100.24'),
               (v_membre, 'COOKIES_ANALYTIQUE', v_version_cookies, TRUE, v_maintenant - interval '40 days', '198.51.100.24'),
               (v_membre, 'COOKIES_MARKETING', v_version_cookies, FALSE, v_maintenant - interval '40 days', '198.51.100.24');

        -- =============================================================================
        -- 5. Rendez-vous couvrant les six statuts
        --
        -- Les deux rendez-vous a venir sont sur des intervalles disjoints du meme poste :
        -- c est ce que ex_rdv_poste_intervalle impose pour EN_ATTENTE et CONFIRME. Les
        -- quatre autres sont passes et sortent du champ de la contrainte.
        -- =============================================================================

        -- CONFIRME : c est celui qui rend l export iCalendar (F38) testable, la route
        -- refusant tout rendez-vous qui ne serait pas confirme.
        INSERT INTO rdv (numero, membre_id, vehicule_id, poste_id, statut, debut, fin,
                         commentaire, created_by, updated_by)
        VALUES ('RDV-' || v_exercice || '-' || lpad(nextval('seq_numero_rdv')::text, 4, '0'),
                v_membre, v_golf, v_poste1, 'CONFIRME',
                v_lundi + interval '9 hours', v_lundi + interval '10 hours',
                'Bruit de freinage a l avant depuis une semaine.', 'demo', 'demo')
        RETURNING id INTO v_rdv_confirme;

        INSERT INTO rdv_service (rdv_id, service_id, quantite, prix_unitaire_htva, taux_tva)
        SELECT v_rdv_confirme, id, 1, prix_htva, taux_tva FROM service WHERE code = 'PLAQUETTES_AV';

        -- EN_ATTENTE : le garage doit encore confirmer ou refuser.
        INSERT INTO rdv (numero, membre_id, vehicule_id, poste_id, statut, debut, fin,
                         commentaire, created_by, updated_by)
        VALUES ('RDV-' || v_exercice || '-' || lpad(nextval('seq_numero_rdv')::text, 4, '0'),
                v_membre, v_clio, v_poste1, 'EN_ATTENTE',
                v_lundi + interval '10 hours 30 minutes', v_lundi + interval '11 hours',
                'Vidange annuelle.', 'demo', 'demo');

        -- HONORE : le client s est presente, un dossier d atelier en decoule.
        INSERT INTO rdv (numero, membre_id, vehicule_id, poste_id, statut, debut, fin,
                         commentaire, created_by, updated_by)
        VALUES ('RDV-' || v_exercice || '-' || lpad(nextval('seq_numero_rdv')::text, 4, '0'),
                v_membre, v_golf, v_poste1, 'HONORE',
                v_lundi - interval '35 days' + interval '9 hours',
                v_lundi - interval '35 days' + interval '10 hours 30 minutes',
                'Revision annuelle.', 'demo', 'demo')
        RETURNING id INTO v_rdv_honore;

        INSERT INTO rdv_service (rdv_id, service_id, quantite, prix_unitaire_htva, taux_tva)
        SELECT v_rdv_honore, id, 1, prix_htva, taux_tva FROM service WHERE code = 'REVISION_ANNUELLE';

        -- ANNULE : annulation par le membre, dans le delai.
        INSERT INTO rdv (numero, membre_id, vehicule_id, poste_id, statut, debut, fin,
                         date_annulation, commentaire, created_by, updated_by)
        VALUES ('RDV-' || v_exercice || '-' || lpad(nextval('seq_numero_rdv')::text, 4, '0'),
                v_membre, v_clio, v_poste2, 'ANNULE',
                v_lundi - interval '21 days' + interval '14 hours',
                v_lundi - interval '21 days' + interval '14 hours 30 minutes',
                v_maintenant - interval '23 days',
                'Permutation des pneus.', 'demo', 'demo');

        -- REFUSE : refus motive du garage, le motif est obligatoire.
        INSERT INTO rdv (numero, membre_id, vehicule_id, poste_id, statut, debut, fin,
                         motif_refus, commentaire, created_by, updated_by)
        VALUES ('RDV-' || v_exercice || '-' || lpad(nextval('seq_numero_rdv')::text, 4, '0'),
                v_membre, v_golf, v_poste2, 'REFUSE',
                v_lundi - interval '14 days' + interval '15 hours',
                v_lundi - interval '14 days' + interval '15 hours 30 minutes',
                'Atelier ferme ce jour pour formation du personnel.',
                'Diagnostic electronique.', 'demo', 'demo');

        -- ABSENT : le creneau est passe sans que le client se presente.
        INSERT INTO rdv (numero, membre_id, vehicule_id, poste_id, statut, debut, fin,
                         commentaire, created_by, updated_by)
        VALUES ('RDV-' || v_exercice || '-' || lpad(nextval('seq_numero_rdv')::text, 4, '0'),
                v_membre, v_clio, v_poste2, 'ABSENT',
                v_lundi - interval '7 days' + interval '11 hours',
                v_lundi - interval '7 days' + interval '11 hours 30 minutes',
                'Montage d un pneu.', 'demo', 'demo');

        -- =============================================================================
        -- 6. Dossiers d atelier : les six etats de la machine
        --
        -- Un seul dossier est rattache a un rendez-vous ; les autres naissent d une
        -- entree directe au garage, ce que rdv_id nullable admet depuis le socle. La
        -- contrainte ck_intervention_origine_unique n autorise de toute facon qu une
        -- origine au plus.
        -- =============================================================================

        -- TERMINEE, issue du rendez-vous honore. Porte l avis et la conversation.
        INSERT INTO intervention (numero, rdv_id, vehicule_id, statut, montant_devis_htva,
                                  debut_reel, fin_reelle, kilometrage_releve, created_by, updated_by)
        VALUES ('INT-' || v_exercice || '-' || lpad(nextval('seq_numero_intervention')::text, 4, '0'),
                v_rdv_honore, v_golf, 'TERMINEE', 180.00,
                v_lundi - interval '35 days' + interval '9 hours 5 minutes',
                v_lundi - interval '35 days' + interval '10 hours 40 minutes',
                95800, 'demo', 'demo')
        RETURNING id INTO v_int_terminee;

        INSERT INTO ligne_intervention (intervention_id, service_id, libelle_fige, quantite,
                                        prix_unitaire_htva, taux_tva, created_by, updated_by)
        SELECT v_int_terminee, id, libelle, 1, prix_htva, taux_tva, 'demo', 'demo'
        FROM service WHERE code = 'REVISION_ANNUELLE';

        INSERT INTO ligne_intervention (intervention_id, piece_id, libelle_fige, quantite,
                                        prix_unitaire_htva, taux_tva, created_by, updated_by)
        VALUES (v_int_terminee, v_filtre, 'Filtre a huile', 1, 12.40, 21.00, 'demo', 'demo');

        -- PLANIFIEE : ouverte, pas encore commencee.
        INSERT INTO intervention (numero, vehicule_id, statut, montant_devis_htva, created_by, updated_by)
        VALUES ('INT-' || v_exercice || '-' || lpad(nextval('seq_numero_intervention')::text, 4, '0'),
                v_clio, 'PLANIFIEE', 65.00, 'demo', 'demo')
        RETURNING id INTO v_int_planifiee;

        INSERT INTO ligne_intervention (intervention_id, service_id, libelle_fige, quantite,
                                        prix_unitaire_htva, taux_tva, created_by, updated_by)
        SELECT v_int_planifiee, id, libelle, 1, prix_htva, taux_tva, 'demo', 'demo'
        FROM service WHERE code = 'VIDANGE_STD';

        -- EN_COURS : travaux commences, horodatage reel pose.
        INSERT INTO intervention (numero, vehicule_id, statut, montant_devis_htva,
                                  debut_reel, kilometrage_releve, created_by, updated_by)
        VALUES ('INT-' || v_exercice || '-' || lpad(nextval('seq_numero_intervention')::text, 4, '0'),
                v_golf, 'EN_COURS', 120.00, v_maintenant - interval '2 hours', 96500, 'demo', 'demo')
        RETURNING id INTO v_int_en_cours;

        INSERT INTO ligne_intervention (intervention_id, service_id, libelle_fige, quantite,
                                        prix_unitaire_htva, taux_tva, created_by, updated_by)
        SELECT v_int_en_cours, id, libelle, 1, prix_htva, taux_tva, 'demo', 'demo'
        FROM service WHERE code = 'PLAQUETTES_AV';

        -- SUSPENDUE : travaux interrompus, piece en attente de livraison.
        INSERT INTO intervention (numero, vehicule_id, statut, montant_devis_htva,
                                  debut_reel, commentaire_admin, kilometrage_releve,
                                  created_by, updated_by)
        VALUES ('INT-' || v_exercice || '-' || lpad(nextval('seq_numero_intervention')::text, 4, '0'),
                v_clio, 'SUSPENDUE', 118.00, v_maintenant - interval '3 days',
                'Batterie non disponible en stock, reapprovisionnement attendu.', 31200,
                'demo', 'demo')
        RETURNING id INTO v_int_suspendue;

        INSERT INTO ligne_intervention (intervention_id, piece_id, libelle_fige, quantite,
                                        prix_unitaire_htva, taux_tva, created_by, updated_by)
        VALUES (v_int_suspendue, v_batterie, 'Batterie 72 Ah', 1, 118.00, 21.00, 'demo', 'demo');

        -- ATTENTE_VALIDATION_MEMBRE : le seuil RM-15 est FRANCHI.
        --
        -- Devis de reference 120,00 HTVA, seuil de declenchement 132,00 (110 %). Les
        -- lignes totalisent 120,00 + 46,50 = 166,50 HTVA, donc au-dela : la ligne ajoutee
        -- en cours attend l accord du membre (accord_membre NULL), et c est ce qui rend
        -- l ecran de validation testable. Un depassement pile a 132,00 ne declencherait
        -- rien, le cahier des charges parlant d un depassement « de plus de » dix pour
        -- cent.
        INSERT INTO intervention (numero, vehicule_id, statut, montant_devis_htva,
                                  debut_reel, kilometrage_releve, created_by, updated_by)
        VALUES ('INT-' || v_exercice || '-' || lpad(nextval('seq_numero_intervention')::text, 4, '0'),
                v_golf, 'ATTENTE_VALIDATION_MEMBRE', 120.00,
                v_maintenant - interval '5 hours', 96520, 'demo', 'demo')
        RETURNING id INTO v_int_validation;

        INSERT INTO ligne_intervention (intervention_id, service_id, libelle_fige, quantite,
                                        prix_unitaire_htva, taux_tva, ajoutee_en_cours,
                                        accord_membre, created_by, updated_by)
        SELECT v_int_validation, id, libelle, 1, prix_htva, taux_tva, FALSE, NULL, 'demo', 'demo'
        FROM service WHERE code = 'PLAQUETTES_AV';

        INSERT INTO ligne_intervention (intervention_id, piece_id, libelle_fige, quantite,
                                        prix_unitaire_htva, taux_tva, ajoutee_en_cours,
                                        accord_membre, created_by, updated_by)
        VALUES (v_int_validation, v_plaquettes, 'Jeu de plaquettes avant', 1, 46.50, 21.00,
                TRUE, NULL, 'demo', 'demo');

        -- ANNULEE : dossier ouvert puis abandonne.
        INSERT INTO intervention (numero, vehicule_id, statut, montant_devis_htva,
                                  commentaire_admin, created_by, updated_by)
        VALUES ('INT-' || v_exercice || '-' || lpad(nextval('seq_numero_intervention')::text, 4, '0'),
                v_clio, 'ANNULEE', 55.00,
                'Le client a fait realiser le diagnostic ailleurs.', 'demo', 'demo')
        RETURNING id INTO v_int_annulee;

        INSERT INTO ligne_intervention (intervention_id, service_id, libelle_fige, quantite,
                                        prix_unitaire_htva, taux_tva, created_by, updated_by)
        SELECT v_int_annulee, id, libelle, 1, prix_htva, taux_tva, 'demo', 'demo'
        FROM service WHERE code = 'DIAG_ELEC';

        -- Chronologie append-only : l ecran de suivi du membre la lit, un dossier sans
        -- historique s y afficherait sans aucune etape.
        INSERT INTO historique_statut_intervention (intervention_id, statut_avant, statut_apres,
                                                    horodatage, auteur_id, motif, created_by, updated_by)
        VALUES (v_int_terminee, NULL, 'PLANIFIEE', v_lundi - interval '35 days' + interval '9 hours', v_admin, NULL, 'demo', 'demo'),
               (v_int_terminee, 'PLANIFIEE', 'EN_COURS', v_lundi - interval '35 days' + interval '9 hours 5 minutes', v_admin, NULL, 'demo', 'demo'),
               (v_int_terminee, 'EN_COURS', 'TERMINEE', v_lundi - interval '35 days' + interval '10 hours 40 minutes', v_admin, NULL, 'demo', 'demo'),
               (v_int_en_cours, NULL, 'PLANIFIEE', v_maintenant - interval '3 hours', v_admin, NULL, 'demo', 'demo'),
               (v_int_en_cours, 'PLANIFIEE', 'EN_COURS', v_maintenant - interval '2 hours', v_admin, NULL, 'demo', 'demo'),
               (v_int_suspendue, NULL, 'PLANIFIEE', v_maintenant - interval '3 days 1 hour', v_admin, NULL, 'demo', 'demo'),
               (v_int_suspendue, 'PLANIFIEE', 'EN_COURS', v_maintenant - interval '3 days', v_admin, NULL, 'demo', 'demo'),
               (v_int_suspendue, 'EN_COURS', 'SUSPENDUE', v_maintenant - interval '2 days 20 hours', v_admin,
                'Batterie non disponible en stock.', 'demo', 'demo'),
               (v_int_validation, NULL, 'PLANIFIEE', v_maintenant - interval '6 hours', v_admin, NULL, 'demo', 'demo'),
               (v_int_validation, 'PLANIFIEE', 'EN_COURS', v_maintenant - interval '5 hours', v_admin, NULL, 'demo', 'demo'),
               (v_int_validation, 'EN_COURS', 'ATTENTE_VALIDATION_MEMBRE', v_maintenant - interval '4 hours', v_admin,
                'Depassement du devis : plaquettes a remplacer.', 'demo', 'demo'),
               (v_int_annulee, NULL, 'PLANIFIEE', v_maintenant - interval '10 days', v_admin, NULL, 'demo', 'demo'),
               (v_int_annulee, 'PLANIFIEE', 'ANNULEE', v_maintenant - interval '9 days', v_admin,
                'Diagnostic realise ailleurs.', 'demo', 'demo');

        -- =============================================================================
        -- 7. Chaine marchande complete : panier -> commande -> paiement -> facture
        --
        -- Le panier reste apres la conversion, VIDE : ce sont ses lignes qui migrent vers
        -- la commande (panier_id devient NULL, commande_id est renseigne), et non des
        -- copies. C est le modele retenu par F14, dont ck_ligne_rattachement_unique fait
        -- un invariant de base. Le panier subsiste donc comme panier courant du membre,
        -- ce que uq_panier_membre_actif limite de toute facon a un seul.
        -- =============================================================================
        INSERT INTO panier (membre_id, created_by, updated_by)
        VALUES (v_membre, 'demo', 'demo')
        RETURNING id INTO v_panier;

        -- 1 filtre a huile a 12,40 + 2 pneus a 89,00 = 190,40 HTVA.
        v_montant_htva := 12.40 + (2 * 89.00);
        v_montant_tva := round(v_montant_htva * 0.21, 2);

        INSERT INTO commande (numero, membre_id, statut, montant_htva, montant_tva, montant_tvac,
                              date_commande, date_paiement, created_by, updated_by)
        VALUES ('CMD-' || v_exercice || '-' || lpad(nextval('seq_numero_commande')::text, 4, '0'),
                v_membre, 'PAYEE', v_montant_htva, v_montant_tva, v_montant_htva + v_montant_tva,
                v_maintenant - interval '12 days', v_maintenant - interval '12 days' + interval '4 minutes',
                'demo', 'demo')
        RETURNING id INTO v_commande;

        INSERT INTO ligne_panier (commande_id, piece_id, libelle_fige, quantite,
                                  prix_unitaire_htva, taux_tva, created_by, updated_by)
        VALUES (v_commande, v_filtre, 'Filtre a huile', 1, 12.40, 21.00, 'demo', 'demo'),
               (v_commande, (SELECT id FROM piece WHERE reference_fabricant = 'PN-2055516'),
                'Pneu 205/55 R16', 2, 89.00, 21.00, 'demo', 'demo');

        -- Le paiement porte une cle d idempotence, comme tout paiement reel : c est elle
        -- qui rend un rejeu de webhook inoffensif. methode reste nulle — seul un vrai
        -- prestataire la renseigne, et l ecran de detail de commande le dit plutot que
        -- d inventer un moyen de paiement.
        INSERT INTO paiement (commande_id, reference_mollie, cle_idempotence, montant, devise,
                              statut, date_initiation, date_finalisation, created_by, updated_by)
        VALUES (v_commande, 'demo_tr_0000000001', 'demo-idem-0000000001',
                v_montant_htva + v_montant_tva, 'EUR', 'REUSSI',
                v_maintenant - interval '12 days', v_maintenant - interval '12 days' + interval '4 minutes',
                'demo', 'demo');

        -- La facture est la premiere de l exercice. chemin_pdf reste nul : le document
        -- est produit a la premiere demande puis archive, et le fabriquer ici supposerait
        -- d ecrire un fichier depuis une migration.
        INSERT INTO facture (numero, exercice, sequence_annuelle, commande_id, membre_id,
                             montant_htva, montant_tva, montant_tvac, taux_tva_applique,
                             date_emission, created_by, updated_by)
        VALUES (v_exercice || '-0001', v_exercice, 1, v_commande, v_membre,
                v_montant_htva, v_montant_tva, v_montant_htva + v_montant_tva, 21.00,
                v_maintenant - interval '12 days' + interval '5 minutes', 'demo', 'demo')
        RETURNING id INTO v_facture;

        -- Sans cette ligne, la premiere facture emise par l application reprendrait a 1
        -- et violerait uq_facture_numero : le compteur est la source de la suite continue.
        INSERT INTO compteur_facture (exercice, dernier_numero)
        VALUES (v_exercice, 1)
        ON CONFLICT (exercice) DO UPDATE SET dernier_numero = GREATEST(compteur_facture.dernier_numero, 1);

        -- Le stock des pieces vendues est decremente, comme le fait le paiement reel.
        UPDATE piece SET quantite_stock = quantite_stock - 1 WHERE id = v_filtre;
        UPDATE piece SET quantite_stock = quantite_stock - 2 WHERE reference_fabricant = 'PN-2055516';

        -- =============================================================================
        -- 8. Avis, messagerie, notifications
        -- =============================================================================

        -- Un avis par intervention (uq_avis_intervention), et seulement sur un dossier
        -- termine : c est la regle de depot du module.
        INSERT INTO avis (membre_id, intervention_id, note, commentaire, publie, signale,
                          date_depot, created_by, updated_by)
        VALUES (v_membre, v_int_terminee, 5,
                'Revision faite dans les temps, explications claires sur les points controles.',
                TRUE, FALSE, v_lundi - interval '34 days', 'demo', 'demo');

        INSERT INTO conversation (membre_id, intervention_id, sujet, cloturee, created_by, updated_by)
        VALUES (v_membre, v_int_validation,
                'Question sur le remplacement des plaquettes', FALSE, 'demo', 'demo')
        RETURNING id INTO v_conversation;

        -- Le role de l expediteur est POSE a l ecriture et non deduit du type de compte :
        -- un membre devenu administrateur ne doit pas reecrire l histoire de ses messages.
        INSERT INTO message (conversation_id, expediteur_id, role_expediteur, corps, lu,
                             date_envoi, created_by, updated_by)
        VALUES (v_conversation, v_membre, 'MEMBRE',
                'Bonjour, les plaquettes sont-elles vraiment a remplacer maintenant ?',
                TRUE, v_maintenant - interval '3 hours', 'demo', 'demo'),
               (v_conversation, v_admin, 'ADMINISTRATEUR',
                'Bonjour, elles sont sous la cote d usure minimale. Le devis est en attente de votre accord.',
                FALSE, v_maintenant - interval '2 hours 30 minutes', 'demo', 'demo');

        -- Le corps ne stocke que l argument : le libelle est resolu a la LECTURE, dans la
        -- langue du membre. Un texte fige a l ecriture serait dans la langue de qui a
        -- declenche la notification, pas de qui la lit.
        --
        -- Le type doit appartenir a l enumeration TypeNotification : la colonne ne porte
        -- AUCUN CHECK, donc la base accepterait n importe quelle chaine et c est
        -- l application qui echouerait a la lecture, sur toute la liste. Constate en
        -- essayant un « INTERVENTION_DEPASSEMENT » qui n existe pas : la migration
        -- passait, et /mes-notifications rendait une erreur 500. Il n existe pas de type
        -- dedie au depassement de devis ; le membre en est averti par le fil de
        -- discussion, d ou MESSAGE_RECU.
        INSERT INTO notification (membre_id, type, titre, corps, statut, canal, date_envoi,
                                  date_lecture, created_by, updated_by)
        VALUES (v_membre, 'RDV_CONFIRME', 'Rendez-vous confirme',
                (SELECT numero FROM rdv WHERE id = v_rdv_confirme),
                'NON_LUE', 'APPLICATION', v_maintenant - interval '1 day', NULL, 'demo', 'demo'),
               (v_membre, 'MESSAGE_RECU', 'Nouveau message du garage',
                (SELECT numero FROM intervention WHERE id = v_int_validation),
                'NON_LUE', 'APPLICATION', v_maintenant - interval '2 hours 30 minutes', NULL, 'demo', 'demo'),
               (v_membre, 'INTERVENTION_TERMINEE', 'Intervention terminee',
                (SELECT numero FROM intervention WHERE id = v_int_terminee),
                'LUE', 'APPLICATION', v_lundi - interval '35 days' + interval '10 hours 45 minutes',
                v_lundi - interval '34 days', 'demo', 'demo'),
               (v_membre, 'COMMANDE_PAYEE', 'Commande payee',
                (SELECT numero FROM commande WHERE id = v_commande),
                'LUE', 'APPLICATION', v_maintenant - interval '12 days',
                v_maintenant - interval '11 days', 'demo', 'demo');

        RAISE NOTICE 'Jeu de demonstration insere : membre=% commande=% facture=%',
            v_membre, v_commande, v_facture;
    END
$$;
