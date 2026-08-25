-- =====================================================================================
-- AutoServ+ — base de demonstration complete (schema + donnees)
--
-- Ce fichier contient le schema integral et un jeu de donnees permettant de parcourir
-- TOUS les ecrans de l application sans rien creer soi-meme : comptes, vehicules,
-- rendez-vous dans les six statuts, dossiers d atelier dans les six etats dont un
-- depassement de devis a valider, une chaine marchande complete (panier, commande,
-- paiement, facture), un avis, un fil de discussion et des notifications.
--
-- Produit par pg_dump depuis une base ou les migrations de schema V1 a V35 ont ete
-- appliquees, suivies de la graine de demonstration V900, profil « demo » actif.
-- 34 tables metier (hors flyway_schema_history).
--
-- La graine porte le numero 900 et non le suivant de la serie : Flyway ne tolere une
-- migration appliquee mais non resolue — ce qu est cette graine pour toute base
-- demarree sans le profil « demo » — que si son numero DEPASSE toutes les migrations
-- connues. En dessous, elle devient « missing » et fait echouer le demarrage.
--
-- Commande exacte, depuis une base vierge :
--
--     docker compose down -v && docker compose up -d
--     mvnw spring-boot:run "-Dspring-boot.run.profiles=demo"
--     docker exec autoservplus-db pg_dump -U autoservplus -d autoservplus --         --clean --if-exists --no-owner
--
-- puis le present en-tete est place en tete du resultat.
--
-- ------------------------------------------------------------------------------------
-- RESTAURATION
-- ------------------------------------------------------------------------------------
-- Depuis la racine du depot, la base de developpement etant demarree
-- (docker compose up -d) :
--
--     docker exec -i autoservplus-db psql -U autoservplus -d autoservplus < docs/dump_autoservplus.sql
--
-- Le fichier commence par des DROP ... IF EXISTS : il remplace integralement le contenu
-- de la base visee. Pour repartir d une base parfaitement vierge :
--
--     docker compose down -v && docker compose up -d
--     docker exec -i autoservplus-db psql -U autoservplus -d autoservplus < docs/dump_autoservplus.sql
--
-- Le dump portant deja flyway_schema_history, l application demarre ensuite sans
-- rejouer aucune migration. Elle emet un avertissement au demarrage, et il est
-- ATTENDU :
--
--     Schema "public" has a version (900) that is newer than the latest available
--     migration (35) !
--
-- C est la graine de demonstration, absente du chemin de Flyway hors du profil
-- « demo ». S il en designe une autre que 900, c est qu une migration de schema manque
-- reellement au depot. Lancer simplement
--
--     mvnw spring-boot:run
--
-- SANS le profil « demo » — le jeu est deja la, et le reclamer une seconde fois ferait
-- echouer Flyway sur une migration deja appliquee.
--
-- ------------------------------------------------------------------------------------
-- COMPTES DE TEST
-- ------------------------------------------------------------------------------------
--   Administrateur   admin@autoservplus.be      ChangezMoi2026!
--   Membre           marie.dupont@demo.test     DemoMembre2026!
--
-- Les empreintes sont de vraies empreintes BCrypt de cout 12. Les adresses sont dans le
-- domaine de premier niveau .test, reserve par la RFC 2606 et non routable : aucun
-- courriel ne peut atteindre une boite reelle. Les noms, plaques et adresses sont
-- inventes ; le jeu ne contient aucune donnee personnelle reelle.
--
-- Le compte administrateur vient de la migration V10 et son mot de passe est celui
-- documente depuis l origine du projet. PUBLIER DES MOTS DE PASSE DANS UN DEPOT est
-- acceptable pour une base de demonstration et A BANNIR EN PRODUCTION : ni ce dump ni
-- le profil « demo » ne doivent etre deployes.
--
-- ------------------------------------------------------------------------------------
-- CE QUE LE JEU PERMET DE TESTER
-- ------------------------------------------------------------------------------------
--   Vitrine            /  /contact  /services  /pieces  /cgv
--                      Le catalogue de pieces etait VIDE avant ce jeu : les six
--                      references qu il ajoute rendent /pieces et le panier utilisables.
--
--   Espace membre      /mes-rendez-vous     six statuts, dont un CONFIRME
--                      /mes-rendez-vous/{reference}/agenda.ics   export iCalendar (F38)
--                      /mes-vehicules       deux vehicules
--                      /commandes           une commande payee et sa facture
--                      /mes-notifications   quatre notifications, lues et non lues
--                      /mes-messages        un fil ouvert avec le garage
--                      /mes-donnees         export RGPD (article 15)
--
--   Administration     /admin                     tableau de bord alimente
--                      /admin/interventions       six etats, dont un depassement de
--                                                 devis en attente de validation (RM-15)
--                      /admin/catalogue/pieces    stock, dont une reference sous son
--                                                 seuil d alerte
--                      /admin/journal             chronologie des transitions
--                      /admin/avis  /admin/messages
--
-- Un rendez-vous reste EN_ATTENTE : c est celui sur lequel l administrateur peut
-- exercer la confirmation ou le refus motive.
--
-- ------------------------------------------------------------------------------------
-- VERSIONS DE DOCUMENTS (F24)
-- ------------------------------------------------------------------------------------
-- Le jeu porte DEUX versions des conditions generales : CGV-2026-02, en vigueur, dont
-- l article 9 annonce dix ans de conservation, et CGV-2026-01, archivee (actif = false),
-- qui annoncait sept ans. La preuve de consentement du membre de demonstration designe
-- CGV-2026-01 — la version en vigueur au moment ou elle a ete ecrite.
--
-- Ce n est pas un defaut du jeu, c est ce qu il sert a montrer : /cgv affiche le texte
-- du jour, et /documents/cgv/CGV-2026-01 rend encore celui que ce membre a reellement
-- accepte. Une preuve doit dire QUOI a ete accepte, pas seulement QUE quelque chose
-- l a ete.
--
-- ------------------------------------------------------------------------------------
-- DATES
-- ------------------------------------------------------------------------------------
-- Le jeu a ete produit avec des dates RELATIVES a son execution : les rendez-vous a
-- venir sont poses deux semaines apres la generation. Ce dump les a donc FIGEES. Pour
-- un jeu recalcule a la date du jour, ne pas restaurer ce fichier et lancer plutot :
--
--     docker compose down -v && docker compose up -d
--     mvnw spring-boot:run "-Dspring-boot.run.profiles=demo"
--
-- =====================================================================================

--
-- PostgreSQL database dump
--

\restrict ENpDnZKxlRPB6NWKBBfJlxn1hG5xGpMmqMX6M6akgBTfuSScBRwwPq4LX6Br4K8

-- Dumped from database version 16.15
-- Dumped by pg_dump version 16.15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS ONLY public.rdv DROP CONSTRAINT IF EXISTS rdv_poste_id_fkey;
ALTER TABLE IF EXISTS ONLY public.indisponibilite DROP CONSTRAINT IF EXISTS indisponibilite_poste_id_fkey;
ALTER TABLE IF EXISTS ONLY public.vehicule DROP CONSTRAINT IF EXISTS fk_vehicule_membre;
ALTER TABLE IF EXISTS ONLY public.service DROP CONSTRAINT IF EXISTS fk_service_categorie;
ALTER TABLE IF EXISTS ONLY public.reservation_parking DROP CONSTRAINT IF EXISTS fk_resa_parking_vehicule;
ALTER TABLE IF EXISTS ONLY public.reservation_parking DROP CONSTRAINT IF EXISTS fk_resa_parking_place;
ALTER TABLE IF EXISTS ONLY public.reservation_parking DROP CONSTRAINT IF EXISTS fk_resa_parking_paiement;
ALTER TABLE IF EXISTS ONLY public.reservation_parking DROP CONSTRAINT IF EXISTS fk_resa_parking_membre;
ALTER TABLE IF EXISTS ONLY public.rdv DROP CONSTRAINT IF EXISTS fk_rdv_vehicule;
ALTER TABLE IF EXISTS ONLY public.rdv_service DROP CONSTRAINT IF EXISTS fk_rdv_service_svc;
ALTER TABLE IF EXISTS ONLY public.rdv_service DROP CONSTRAINT IF EXISTS fk_rdv_service_rdv;
ALTER TABLE IF EXISTS ONLY public.rdv DROP CONSTRAINT IF EXISTS fk_rdv_membre;
ALTER TABLE IF EXISTS ONLY public.piece DROP CONSTRAINT IF EXISTS fk_piece_categorie;
ALTER TABLE IF EXISTS ONLY public.photo DROP CONSTRAINT IF EXISTS fk_photo_service;
ALTER TABLE IF EXISTS ONLY public.photo DROP CONSTRAINT IF EXISTS fk_photo_piece;
ALTER TABLE IF EXISTS ONLY public.photo DROP CONSTRAINT IF EXISTS fk_photo_intervention;
ALTER TABLE IF EXISTS ONLY public.panier DROP CONSTRAINT IF EXISTS fk_panier_membre;
ALTER TABLE IF EXISTS ONLY public.paiement DROP CONSTRAINT IF EXISTS fk_paiement_commande;
ALTER TABLE IF EXISTS ONLY public.notification DROP CONSTRAINT IF EXISTS fk_notification_membre;
ALTER TABLE IF EXISTS ONLY public.message DROP CONSTRAINT IF EXISTS fk_message_expediteur;
ALTER TABLE IF EXISTS ONLY public.message DROP CONSTRAINT IF EXISTS fk_message_conversation;
ALTER TABLE IF EXISTS ONLY public.ligne_panier DROP CONSTRAINT IF EXISTS fk_ligne_panier_service;
ALTER TABLE IF EXISTS ONLY public.ligne_panier DROP CONSTRAINT IF EXISTS fk_ligne_panier_piece;
ALTER TABLE IF EXISTS ONLY public.ligne_panier DROP CONSTRAINT IF EXISTS fk_ligne_panier_panier;
ALTER TABLE IF EXISTS ONLY public.ligne_panier DROP CONSTRAINT IF EXISTS fk_ligne_panier_commande;
ALTER TABLE IF EXISTS ONLY public.ligne_intervention DROP CONSTRAINT IF EXISTS fk_ligne_interv_service;
ALTER TABLE IF EXISTS ONLY public.ligne_intervention DROP CONSTRAINT IF EXISTS fk_ligne_interv_piece;
ALTER TABLE IF EXISTS ONLY public.ligne_intervention DROP CONSTRAINT IF EXISTS fk_ligne_interv_interv;
ALTER TABLE IF EXISTS ONLY public.intervention DROP CONSTRAINT IF EXISTS fk_intervention_vehicule;
ALTER TABLE IF EXISTS ONLY public.intervention DROP CONSTRAINT IF EXISTS fk_intervention_rdv;
ALTER TABLE IF EXISTS ONLY public.intervention DROP CONSTRAINT IF EXISTS fk_intervention_commande;
ALTER TABLE IF EXISTS ONLY public.historique_statut_intervention DROP CONSTRAINT IF EXISTS fk_histo_statut_interv;
ALTER TABLE IF EXISTS ONLY public.historique_statut_intervention DROP CONSTRAINT IF EXISTS fk_histo_statut_auteur;
ALTER TABLE IF EXISTS ONLY public.historique_modification_catalogue DROP CONSTRAINT IF EXISTS fk_histo_catalogue_auteur;
ALTER TABLE IF EXISTS ONLY public.facture DROP CONSTRAINT IF EXISTS fk_facture_membre;
ALTER TABLE IF EXISTS ONLY public.facture DROP CONSTRAINT IF EXISTS fk_facture_intervention;
ALTER TABLE IF EXISTS ONLY public.facture DROP CONSTRAINT IF EXISTS fk_facture_commande;
ALTER TABLE IF EXISTS ONLY public.demande_annulation DROP CONSTRAINT IF EXISTS fk_demande_annulation_decideur;
ALTER TABLE IF EXISTS ONLY public.demande_annulation DROP CONSTRAINT IF EXISTS fk_demande_annulation_commande;
ALTER TABLE IF EXISTS ONLY public.demande_annulation DROP CONSTRAINT IF EXISTS fk_demande_annulation_avoir;
ALTER TABLE IF EXISTS ONLY public.conversation DROP CONSTRAINT IF EXISTS fk_conversation_membre;
ALTER TABLE IF EXISTS ONLY public.conversation DROP CONSTRAINT IF EXISTS fk_conversation_interv;
ALTER TABLE IF EXISTS ONLY public.consentement DROP CONSTRAINT IF EXISTS fk_consentement_utilisateur;
ALTER TABLE IF EXISTS ONLY public.commande DROP CONSTRAINT IF EXISTS fk_commande_membre;
ALTER TABLE IF EXISTS ONLY public.avoir DROP CONSTRAINT IF EXISTS fk_avoir_facture;
ALTER TABLE IF EXISTS ONLY public.avis DROP CONSTRAINT IF EXISTS fk_avis_membre;
ALTER TABLE IF EXISTS ONLY public.avis DROP CONSTRAINT IF EXISTS fk_avis_intervention;
DROP TRIGGER IF EXISTS tg_facture_immuable ON public.facture;
DROP TRIGGER IF EXISTS tg_avoir_immuable ON public.avoir;
DROP INDEX IF EXISTS public.uq_vehicule_plaque_active;
DROP INDEX IF EXISTS public.uq_poste_libelle_actif;
DROP INDEX IF EXISTS public.uq_panier_membre_actif;
DROP INDEX IF EXISTS public.uq_paiement_remboursement;
DROP INDEX IF EXISTS public.uq_facture_intervention;
DROP INDEX IF EXISTS public.uq_facture_commande;
DROP INDEX IF EXISTS public.uq_demande_annulation_en_attente;
DROP INDEX IF EXISTS public.uq_avoir_facture;
DROP INDEX IF EXISTS public.ix_version_document_en_vigueur;
DROP INDEX IF EXISTS public.ix_vehicule_membre;
DROP INDEX IF EXISTS public.ix_utilisateur_statut;
DROP INDEX IF EXISTS public.ix_utilisateur_jeton;
DROP INDEX IF EXISTS public.ix_utilisateur_email;
DROP INDEX IF EXISTS public.ix_service_categorie;
DROP INDEX IF EXISTS public.ix_service_actif;
DROP INDEX IF EXISTS public.ix_resa_parking_place;
DROP INDEX IF EXISTS public.ix_resa_parking_membre;
DROP INDEX IF EXISTS public.ix_rdv_statut;
DROP INDEX IF EXISTS public.ix_rdv_poste_debut;
DROP INDEX IF EXISTS public.ix_rdv_membre;
DROP INDEX IF EXISTS public.ix_piece_categorie;
DROP INDEX IF EXISTS public.ix_photo_service;
DROP INDEX IF EXISTS public.ix_photo_piece;
DROP INDEX IF EXISTS public.ix_photo_intervention;
DROP INDEX IF EXISTS public.ix_paiement_statut;
DROP INDEX IF EXISTS public.ix_paiement_commande;
DROP INDEX IF EXISTS public.ix_notification_membre;
DROP INDEX IF EXISTS public.ix_message_conversation;
DROP INDEX IF EXISTS public.ix_ligne_panier_panier;
DROP INDEX IF EXISTS public.ix_ligne_panier_commande;
DROP INDEX IF EXISTS public.ix_ligne_intervention;
DROP INDEX IF EXISTS public.ix_intervention_vehicule;
DROP INDEX IF EXISTS public.ix_intervention_statut;
DROP INDEX IF EXISTS public.ix_intervention_rdv;
DROP INDEX IF EXISTS public.ix_intervention_commande;
DROP INDEX IF EXISTS public.ix_indispo_intervalle;
DROP INDEX IF EXISTS public.ix_historique_statut_intervention;
DROP INDEX IF EXISTS public.ix_historique_modification_catalogue;
DROP INDEX IF EXISTS public.ix_facture_membre;
DROP INDEX IF EXISTS public.ix_facture_emission;
DROP INDEX IF EXISTS public.ix_demande_annulation_statut;
DROP INDEX IF EXISTS public.ix_demande_annulation_commande;
DROP INDEX IF EXISTS public.ix_conversation_membre;
DROP INDEX IF EXISTS public.ix_consentement_utilisateur;
DROP INDEX IF EXISTS public.ix_commande_statut;
DROP INDEX IF EXISTS public.ix_commande_membre;
DROP INDEX IF EXISTS public.ix_categorie_type;
DROP INDEX IF EXISTS public.flyway_schema_history_s_idx;
ALTER TABLE IF EXISTS ONLY public.version_document DROP CONSTRAINT IF EXISTS version_document_pkey;
ALTER TABLE IF EXISTS ONLY public.vehicule DROP CONSTRAINT IF EXISTS vehicule_pkey;
ALTER TABLE IF EXISTS ONLY public.utilisateur DROP CONSTRAINT IF EXISTS utilisateur_pkey;
ALTER TABLE IF EXISTS ONLY public.version_document DROP CONSTRAINT IF EXISTS uq_version_document;
ALTER TABLE IF EXISTS ONLY public.vehicule DROP CONSTRAINT IF EXISTS uq_vehicule_reference;
ALTER TABLE IF EXISTS ONLY public.utilisateur DROP CONSTRAINT IF EXISTS uq_utilisateur_reference;
ALTER TABLE IF EXISTS ONLY public.utilisateur DROP CONSTRAINT IF EXISTS uq_utilisateur_email;
ALTER TABLE IF EXISTS ONLY public.service DROP CONSTRAINT IF EXISTS uq_service_reference;
ALTER TABLE IF EXISTS ONLY public.service DROP CONSTRAINT IF EXISTS uq_service_code;
ALTER TABLE IF EXISTS ONLY public.reservation_parking DROP CONSTRAINT IF EXISTS uq_resa_parking_reference;
ALTER TABLE IF EXISTS ONLY public.reservation_parking DROP CONSTRAINT IF EXISTS uq_resa_parking_numero;
ALTER TABLE IF EXISTS ONLY public.rdv_service DROP CONSTRAINT IF EXISTS uq_rdv_service;
ALTER TABLE IF EXISTS ONLY public.rdv DROP CONSTRAINT IF EXISTS uq_rdv_reference;
ALTER TABLE IF EXISTS ONLY public.rdv DROP CONSTRAINT IF EXISTS uq_rdv_numero;
ALTER TABLE IF EXISTS ONLY public.poste_atelier DROP CONSTRAINT IF EXISTS uq_poste_reference;
ALTER TABLE IF EXISTS ONLY public.place_parking DROP CONSTRAINT IF EXISTS uq_place_parking_numero;
ALTER TABLE IF EXISTS ONLY public.piece DROP CONSTRAINT IF EXISTS uq_piece_reference;
ALTER TABLE IF EXISTS ONLY public.piece DROP CONSTRAINT IF EXISTS uq_piece_fabricant;
ALTER TABLE IF EXISTS ONLY public.panier DROP CONSTRAINT IF EXISTS uq_panier_reference;
ALTER TABLE IF EXISTS ONLY public.paiement DROP CONSTRAINT IF EXISTS uq_paiement_reference;
ALTER TABLE IF EXISTS ONLY public.paiement DROP CONSTRAINT IF EXISTS uq_paiement_mollie;
ALTER TABLE IF EXISTS ONLY public.paiement DROP CONSTRAINT IF EXISTS uq_paiement_idempotence;
ALTER TABLE IF EXISTS ONLY public.intervention DROP CONSTRAINT IF EXISTS uq_intervention_reference;
ALTER TABLE IF EXISTS ONLY public.intervention DROP CONSTRAINT IF EXISTS uq_intervention_numero;
ALTER TABLE IF EXISTS ONLY public.indisponibilite DROP CONSTRAINT IF EXISTS uq_indispo_reference;
ALTER TABLE IF EXISTS ONLY public.facture DROP CONSTRAINT IF EXISTS uq_facture_sequence;
ALTER TABLE IF EXISTS ONLY public.facture DROP CONSTRAINT IF EXISTS uq_facture_reference;
ALTER TABLE IF EXISTS ONLY public.facture DROP CONSTRAINT IF EXISTS uq_facture_numero;
ALTER TABLE IF EXISTS ONLY public.demande_annulation DROP CONSTRAINT IF EXISTS uq_demande_annulation_reference;
ALTER TABLE IF EXISTS ONLY public.conversation DROP CONSTRAINT IF EXISTS uq_conversation_reference;
ALTER TABLE IF EXISTS ONLY public.commande DROP CONSTRAINT IF EXISTS uq_commande_reference;
ALTER TABLE IF EXISTS ONLY public.commande DROP CONSTRAINT IF EXISTS uq_commande_numero;
ALTER TABLE IF EXISTS ONLY public.clef_api DROP CONSTRAINT IF EXISTS uq_clef_api_reference;
ALTER TABLE IF EXISTS ONLY public.clef_api DROP CONSTRAINT IF EXISTS uq_clef_api_hachee;
ALTER TABLE IF EXISTS ONLY public.categorie DROP CONSTRAINT IF EXISTS uq_categorie_code;
ALTER TABLE IF EXISTS ONLY public.avoir DROP CONSTRAINT IF EXISTS uq_avoir_reference;
ALTER TABLE IF EXISTS ONLY public.avoir DROP CONSTRAINT IF EXISTS uq_avoir_numero;
ALTER TABLE IF EXISTS ONLY public.avis DROP CONSTRAINT IF EXISTS uq_avis_reference;
ALTER TABLE IF EXISTS ONLY public.avis DROP CONSTRAINT IF EXISTS uq_avis_intervention;
ALTER TABLE IF EXISTS ONLY public.service DROP CONSTRAINT IF EXISTS service_pkey;
ALTER TABLE IF EXISTS ONLY public.reservation_parking DROP CONSTRAINT IF EXISTS reservation_parking_pkey;
ALTER TABLE IF EXISTS ONLY public.rdv_service DROP CONSTRAINT IF EXISTS rdv_service_pkey;
ALTER TABLE IF EXISTS ONLY public.rdv DROP CONSTRAINT IF EXISTS rdv_pkey;
ALTER TABLE IF EXISTS ONLY public.poste_atelier DROP CONSTRAINT IF EXISTS poste_atelier_pkey;
ALTER TABLE IF EXISTS ONLY public.plage_ouverture DROP CONSTRAINT IF EXISTS plage_ouverture_pkey;
ALTER TABLE IF EXISTS ONLY public.place_parking DROP CONSTRAINT IF EXISTS place_parking_pkey;
ALTER TABLE IF EXISTS ONLY public.piece DROP CONSTRAINT IF EXISTS piece_pkey;
ALTER TABLE IF EXISTS ONLY public.photo DROP CONSTRAINT IF EXISTS photo_pkey;
ALTER TABLE IF EXISTS ONLY public.parametre_atelier DROP CONSTRAINT IF EXISTS parametre_atelier_pkey;
ALTER TABLE IF EXISTS ONLY public.panier DROP CONSTRAINT IF EXISTS panier_pkey;
ALTER TABLE IF EXISTS ONLY public.paiement DROP CONSTRAINT IF EXISTS paiement_pkey;
ALTER TABLE IF EXISTS ONLY public.notification DROP CONSTRAINT IF EXISTS notification_pkey;
ALTER TABLE IF EXISTS ONLY public.message DROP CONSTRAINT IF EXISTS message_pkey;
ALTER TABLE IF EXISTS ONLY public.ligne_panier DROP CONSTRAINT IF EXISTS ligne_panier_pkey;
ALTER TABLE IF EXISTS ONLY public.ligne_intervention DROP CONSTRAINT IF EXISTS ligne_intervention_pkey;
ALTER TABLE IF EXISTS ONLY public.intervention DROP CONSTRAINT IF EXISTS intervention_pkey;
ALTER TABLE IF EXISTS ONLY public.indisponibilite DROP CONSTRAINT IF EXISTS indisponibilite_pkey;
ALTER TABLE IF EXISTS ONLY public.historique_statut_intervention DROP CONSTRAINT IF EXISTS historique_statut_intervention_pkey;
ALTER TABLE IF EXISTS ONLY public.historique_modification_catalogue DROP CONSTRAINT IF EXISTS historique_modification_catalogue_pkey;
ALTER TABLE IF EXISTS ONLY public.flyway_schema_history DROP CONSTRAINT IF EXISTS flyway_schema_history_pk;
ALTER TABLE IF EXISTS ONLY public.facture DROP CONSTRAINT IF EXISTS facture_pkey;
ALTER TABLE IF EXISTS ONLY public.rdv DROP CONSTRAINT IF EXISTS ex_rdv_poste_intervalle;
ALTER TABLE IF EXISTS ONLY public.plage_ouverture DROP CONSTRAINT IF EXISTS ex_plage_ouverture_chevauchement;
ALTER TABLE IF EXISTS ONLY public.demande_annulation DROP CONSTRAINT IF EXISTS demande_annulation_pkey;
ALTER TABLE IF EXISTS ONLY public.conversation DROP CONSTRAINT IF EXISTS conversation_pkey;
ALTER TABLE IF EXISTS ONLY public.consentement DROP CONSTRAINT IF EXISTS consentement_pkey;
ALTER TABLE IF EXISTS ONLY public.compteur_facture DROP CONSTRAINT IF EXISTS compteur_facture_pkey;
ALTER TABLE IF EXISTS ONLY public.compteur_avoir DROP CONSTRAINT IF EXISTS compteur_avoir_pkey;
ALTER TABLE IF EXISTS ONLY public.commande DROP CONSTRAINT IF EXISTS commande_pkey;
ALTER TABLE IF EXISTS ONLY public.clef_api DROP CONSTRAINT IF EXISTS clef_api_pkey;
ALTER TABLE IF EXISTS ONLY public.categorie DROP CONSTRAINT IF EXISTS categorie_pkey;
ALTER TABLE IF EXISTS ONLY public.avoir DROP CONSTRAINT IF EXISTS avoir_pkey;
ALTER TABLE IF EXISTS ONLY public.avis DROP CONSTRAINT IF EXISTS avis_pkey;
ALTER TABLE IF EXISTS public.version_document ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.vehicule ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.utilisateur ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.service ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.reservation_parking ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.rdv_service ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.rdv ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.poste_atelier ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.plage_ouverture ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.place_parking ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.piece ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.photo ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.panier ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.paiement ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.notification ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.message ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.ligne_panier ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.ligne_intervention ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.intervention ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.indisponibilite ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.historique_statut_intervention ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.historique_modification_catalogue ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.facture ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.demande_annulation ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.conversation ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.consentement ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.commande ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.clef_api ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.categorie ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.avoir ALTER COLUMN id DROP DEFAULT;
ALTER TABLE IF EXISTS public.avis ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE IF EXISTS public.version_document_id_seq;
DROP TABLE IF EXISTS public.version_document;
DROP SEQUENCE IF EXISTS public.vehicule_id_seq;
DROP TABLE IF EXISTS public.vehicule;
DROP SEQUENCE IF EXISTS public.utilisateur_id_seq;
DROP TABLE IF EXISTS public.utilisateur;
DROP SEQUENCE IF EXISTS public.service_id_seq;
DROP TABLE IF EXISTS public.service;
DROP SEQUENCE IF EXISTS public.seq_numero_rdv;
DROP SEQUENCE IF EXISTS public.seq_numero_parking;
DROP SEQUENCE IF EXISTS public.seq_numero_intervention;
DROP SEQUENCE IF EXISTS public.seq_numero_facture;
DROP SEQUENCE IF EXISTS public.seq_numero_commande;
DROP SEQUENCE IF EXISTS public.seq_numero_avoir;
DROP SEQUENCE IF EXISTS public.reservation_parking_id_seq;
DROP TABLE IF EXISTS public.reservation_parking;
DROP SEQUENCE IF EXISTS public.rdv_service_id_seq;
DROP TABLE IF EXISTS public.rdv_service;
DROP SEQUENCE IF EXISTS public.rdv_numero_seq;
DROP SEQUENCE IF EXISTS public.rdv_id_seq;
DROP TABLE IF EXISTS public.rdv;
DROP SEQUENCE IF EXISTS public.poste_atelier_id_seq;
DROP TABLE IF EXISTS public.poste_atelier;
DROP SEQUENCE IF EXISTS public.plage_ouverture_id_seq;
DROP TABLE IF EXISTS public.plage_ouverture;
DROP SEQUENCE IF EXISTS public.place_parking_id_seq;
DROP TABLE IF EXISTS public.place_parking;
DROP SEQUENCE IF EXISTS public.piece_id_seq;
DROP TABLE IF EXISTS public.piece;
DROP SEQUENCE IF EXISTS public.photo_id_seq;
DROP TABLE IF EXISTS public.photo;
DROP TABLE IF EXISTS public.parametre_atelier;
DROP SEQUENCE IF EXISTS public.panier_id_seq;
DROP TABLE IF EXISTS public.panier;
DROP SEQUENCE IF EXISTS public.paiement_id_seq;
DROP TABLE IF EXISTS public.paiement;
DROP SEQUENCE IF EXISTS public.notification_id_seq;
DROP TABLE IF EXISTS public.notification;
DROP SEQUENCE IF EXISTS public.message_id_seq;
DROP TABLE IF EXISTS public.message;
DROP SEQUENCE IF EXISTS public.ligne_panier_id_seq;
DROP TABLE IF EXISTS public.ligne_panier;
DROP SEQUENCE IF EXISTS public.ligne_intervention_id_seq;
DROP TABLE IF EXISTS public.ligne_intervention;
DROP SEQUENCE IF EXISTS public.intervention_id_seq;
DROP TABLE IF EXISTS public.intervention;
DROP SEQUENCE IF EXISTS public.indisponibilite_id_seq;
DROP TABLE IF EXISTS public.indisponibilite;
DROP SEQUENCE IF EXISTS public.historique_statut_intervention_id_seq;
DROP TABLE IF EXISTS public.historique_statut_intervention;
DROP SEQUENCE IF EXISTS public.historique_modification_catalogue_id_seq;
DROP TABLE IF EXISTS public.historique_modification_catalogue;
DROP TABLE IF EXISTS public.flyway_schema_history;
DROP SEQUENCE IF EXISTS public.facture_id_seq;
DROP TABLE IF EXISTS public.facture;
DROP SEQUENCE IF EXISTS public.demande_annulation_id_seq;
DROP TABLE IF EXISTS public.demande_annulation;
DROP SEQUENCE IF EXISTS public.conversation_id_seq;
DROP TABLE IF EXISTS public.conversation;
DROP SEQUENCE IF EXISTS public.consentement_id_seq;
DROP TABLE IF EXISTS public.consentement;
DROP TABLE IF EXISTS public.compteur_facture;
DROP TABLE IF EXISTS public.compteur_avoir;
DROP SEQUENCE IF EXISTS public.commande_id_seq;
DROP TABLE IF EXISTS public.commande;
DROP SEQUENCE IF EXISTS public.clef_api_id_seq;
DROP TABLE IF EXISTS public.clef_api;
DROP SEQUENCE IF EXISTS public.categorie_id_seq;
DROP TABLE IF EXISTS public.categorie;
DROP SEQUENCE IF EXISTS public.avoir_id_seq;
DROP TABLE IF EXISTS public.avoir;
DROP SEQUENCE IF EXISTS public.avis_id_seq;
DROP TABLE IF EXISTS public.avis;
DROP FUNCTION IF EXISTS public.fn_tables_traces_audit();
DROP FUNCTION IF EXISTS public.fn_facture_immuable();
DROP FUNCTION IF EXISTS public.fn_avoir_immuable();
DROP FUNCTION IF EXISTS public.fn_anonymiser_traces_audit(ancien text, jeton text);
DROP EXTENSION IF EXISTS pgcrypto;
DROP EXTENSION IF EXISTS btree_gist;
--
-- Name: btree_gist; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;


--
-- Name: EXTENSION btree_gist; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION btree_gist IS 'support for indexing common datatypes in GiST';


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: fn_anonymiser_traces_audit(text, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_anonymiser_traces_audit(ancien text, jeton text) RETURNS integer
    LANGUAGE plpgsql
    AS $_$
DECLARE
    cible     RECORD;
    modifiees INTEGER := 0;
    lot       INTEGER;
BEGIN
    IF ancien IS NULL OR jeton IS NULL OR ancien = jeton THEN
        RETURN 0;
    END IF;
    FOR cible IN SELECT nom_table, nom_colonne FROM fn_tables_traces_audit() LOOP
        EXECUTE format('UPDATE %I SET %I = $1 WHERE %I = $2',
                       cible.nom_table, cible.nom_colonne, cible.nom_colonne)
            USING jeton, ancien;
        GET DIAGNOSTICS lot = ROW_COUNT;
        modifiees := modifiees + lot;
    END LOOP;
    RETURN modifiees;
END;
$_$;


--
-- Name: FUNCTION fn_anonymiser_traces_audit(ancien text, jeton text); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.fn_anonymiser_traces_audit(ancien text, jeton text) IS 'Remplace une adresse de courriel par le jeton anonyme du compte dans les colonnes declarees par fn_tables_traces_audit() (F23). Ces colonnes stockent le principal authentifie, donc l adresse en clair : les vider fait partie de l effacement. Le remplacement est le jeton anonyme-{id}@supprime.invalid et non NULL : les colonnes sont nullables, mais une trace d audit doit rester resoluble — savoir QUE la ligne a ete creee par le compte 42, desormais anonymise, reste une information de tracabilite ; un NULL la perdrait. Ne touche aucune donnee comptable : les triggers d immuabilite gardent les montants, numeros et dates, jamais les colonnes d audit.';


--
-- Name: fn_avoir_immuable(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_avoir_immuable() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.numero        IS DISTINCT FROM NEW.numero
       OR OLD.facture_id    IS DISTINCT FROM NEW.facture_id
       OR OLD.montant_htva  IS DISTINCT FROM NEW.montant_htva
       OR OLD.montant_tva   IS DISTINCT FROM NEW.montant_tva
       OR OLD.montant_tvac  IS DISTINCT FROM NEW.montant_tvac
       OR OLD.motif         IS DISTINCT FROM NEW.motif
       OR OLD.date_emission IS DISTINCT FROM NEW.date_emission THEN
        RAISE EXCEPTION 'Une note de credit emise est immuable.';
END IF;
RETURN NEW;
END;
$$;


--
-- Name: fn_facture_immuable(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_facture_immuable() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.numero            IS DISTINCT FROM NEW.numero
       OR OLD.montant_htva      IS DISTINCT FROM NEW.montant_htva
       OR OLD.montant_tva       IS DISTINCT FROM NEW.montant_tva
       OR OLD.montant_tvac      IS DISTINCT FROM NEW.montant_tvac
       OR OLD.taux_tva_applique IS DISTINCT FROM NEW.taux_tva_applique
       OR OLD.date_emission     IS DISTINCT FROM NEW.date_emission THEN
        RAISE EXCEPTION 'Une facture emise est immuable. Emettez une note de credit.';
END IF;
RETURN NEW;
END;
$$;


--
-- Name: fn_tables_traces_audit(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_tables_traces_audit() RETURNS TABLE(nom_table text, nom_colonne text)
    LANGUAGE sql IMMUTABLE
    AS $$
    VALUES
        ('avis'::TEXT, 'created_by'::TEXT),
        ('avis', 'updated_by'),
        ('avoir', 'created_by'),
        ('avoir', 'updated_by'),
        ('categorie', 'created_by'),
        ('categorie', 'updated_by'),
        ('clef_api', 'created_by'),
        ('clef_api', 'updated_by'),
        ('commande', 'created_by'),
        ('commande', 'updated_by'),
        ('compteur_avoir', 'updated_by'),
        ('compteur_facture', 'updated_by'),
        ('consentement', 'created_by'),
        ('consentement', 'updated_by'),
        ('conversation', 'created_by'),
        ('conversation', 'updated_by'),
        ('demande_annulation', 'created_by'),
        ('demande_annulation', 'updated_by'),
        ('facture', 'created_by'),
        ('facture', 'updated_by'),
        ('historique_modification_catalogue', 'created_by'),
        ('historique_modification_catalogue', 'updated_by'),
        ('historique_statut_intervention', 'created_by'),
        ('historique_statut_intervention', 'updated_by'),
        ('indisponibilite', 'created_by'),
        ('indisponibilite', 'updated_by'),
        ('intervention', 'created_by'),
        ('intervention', 'updated_by'),
        ('ligne_intervention', 'created_by'),
        ('ligne_intervention', 'updated_by'),
        ('ligne_panier', 'created_by'),
        ('ligne_panier', 'updated_by'),
        ('message', 'created_by'),
        ('message', 'updated_by'),
        ('notification', 'created_by'),
        ('notification', 'updated_by'),
        ('paiement', 'created_by'),
        ('paiement', 'updated_by'),
        ('panier', 'created_by'),
        ('panier', 'updated_by'),
        ('parametre_atelier', 'updated_by'),
        ('photo', 'created_by'),
        ('photo', 'updated_by'),
        ('piece', 'created_by'),
        ('piece', 'updated_by'),
        ('place_parking', 'created_by'),
        ('place_parking', 'updated_by'),
        ('plage_ouverture', 'created_by'),
        ('plage_ouverture', 'updated_by'),
        ('poste_atelier', 'created_by'),
        ('poste_atelier', 'updated_by'),
        ('rdv', 'created_by'),
        ('rdv', 'updated_by'),
        ('rdv_service', 'created_by'),
        ('rdv_service', 'updated_by'),
        ('reservation_parking', 'created_by'),
        ('reservation_parking', 'updated_by'),
        ('service', 'created_by'),
        ('service', 'updated_by'),
        ('utilisateur', 'created_by'),
        ('utilisateur', 'updated_by'),
        ('vehicule', 'created_by'),
        ('vehicule', 'updated_by'),
        ('version_document', 'created_by'),
        ('version_document', 'updated_by');
$$;


--
-- Name: FUNCTION fn_tables_traces_audit(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.fn_tables_traces_audit() IS 'Liste EXPLICITE des colonnes d audit balayees a l anonymisation d un compte (F23). Enumeree et non derivee du catalogue : un effacement legal doit pouvoir enoncer exactement ce qu il ecrase, et la reponse ne doit pas dependre de l etat de la base au moment de l appel. Un SELECT sur cette fonction est la reponse auditable. SchemaIT confronte cette liste au schema reel et echoue si une colonne manque : une table ajoutee plus tard casse la build au lieu de fuiter en silence.';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: avis; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.avis (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    membre_id bigint NOT NULL,
    intervention_id bigint NOT NULL,
    note smallint NOT NULL,
    commentaire text,
    publie boolean DEFAULT true NOT NULL,
    signale boolean DEFAULT false NOT NULL,
    date_depot timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_avis_note CHECK (((note >= 1) AND (note <= 5)))
);


--
-- Name: avis_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.avis_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: avis_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.avis_id_seq OWNED BY public.avis.id;


--
-- Name: avoir; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.avoir (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    numero character varying(20) NOT NULL,
    facture_id bigint NOT NULL,
    montant_htva numeric(10,2) NOT NULL,
    montant_tva numeric(10,2) NOT NULL,
    montant_tvac numeric(10,2) NOT NULL,
    motif text NOT NULL,
    date_emission timestamp with time zone DEFAULT now() NOT NULL,
    chemin_pdf character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    CONSTRAINT ck_avoir_coherence CHECK ((montant_tvac = (montant_htva + montant_tva))),
    CONSTRAINT ck_avoir_montants CHECK (((montant_htva >= (0)::numeric) AND (montant_tva >= (0)::numeric) AND (montant_tvac >= (0)::numeric)))
);


--
-- Name: TABLE avoir; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.avoir IS 'Note de credit : seul moyen legal de corriger une facture deja emise.';


--
-- Name: avoir_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.avoir_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: avoir_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.avoir_id_seq OWNED BY public.avoir.id;


--
-- Name: categorie; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categorie (
    id bigint NOT NULL,
    code character varying(40) NOT NULL,
    libelle character varying(120) NOT NULL,
    type character varying(20) NOT NULL,
    description text,
    ordre smallint DEFAULT 0 NOT NULL,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_categorie_type CHECK (((type)::text = ANY ((ARRAY['SERVICE'::character varying, 'PIECE'::character varying])::text[])))
);


--
-- Name: categorie_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.categorie_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categorie_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.categorie_id_seq OWNED BY public.categorie.id;


--
-- Name: clef_api; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clef_api (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    libelle character varying(120) NOT NULL,
    clef_hachee character varying(64) NOT NULL,
    quota_minute integer DEFAULT 60 NOT NULL,
    actif boolean DEFAULT true NOT NULL,
    date_expiration timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_clef_api_quota CHECK ((quota_minute > 0))
);


--
-- Name: COLUMN clef_api.clef_hachee; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.clef_api.clef_hachee IS 'Empreinte SHA-256. La valeur en clair n est jamais stockee.';


--
-- Name: clef_api_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.clef_api_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: clef_api_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.clef_api_id_seq OWNED BY public.clef_api.id;


--
-- Name: commande; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.commande (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    numero character varying(20) NOT NULL,
    membre_id bigint NOT NULL,
    statut character varying(25) DEFAULT 'EN_ATTENTE_PAIEMENT'::character varying NOT NULL,
    montant_htva numeric(10,2) NOT NULL,
    montant_tva numeric(10,2) NOT NULL,
    montant_tvac numeric(10,2) NOT NULL,
    date_commande timestamp with time zone DEFAULT now() NOT NULL,
    date_paiement timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    motif_annulation character varying(30),
    date_annulation timestamp with time zone,
    rupture_a_honorer boolean DEFAULT false NOT NULL,
    renonciation_vi53 boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_commande_annulation CHECK ((((statut)::text <> 'ANNULEE'::text) OR ((motif_annulation IS NOT NULL) AND (date_annulation IS NOT NULL)))),
    CONSTRAINT ck_commande_coherence CHECK ((montant_tvac = (montant_htva + montant_tva))),
    CONSTRAINT ck_commande_montants CHECK (((montant_htva >= (0)::numeric) AND (montant_tva >= (0)::numeric) AND (montant_tvac >= (0)::numeric))),
    CONSTRAINT ck_commande_motif_annulation CHECK (((motif_annulation IS NULL) OR ((motif_annulation)::text = ANY ((ARRAY['TIMEOUT_PAIEMENT'::character varying, 'ABANDON_PAIEMENT'::character varying, 'ECHEC_DEFINITIF'::character varying, 'ANNULATION_MEMBRE'::character varying, 'RETRACTATION_F30'::character varying, 'EXCEPTION_ADMIN'::character varying])::text[])))),
    CONSTRAINT ck_commande_statut CHECK (((statut)::text = ANY ((ARRAY['EN_ATTENTE_PAIEMENT'::character varying, 'PAYEE'::character varying, 'ANNULEE'::character varying, 'REMBOURSEE'::character varying])::text[])))
);


--
-- Name: COLUMN commande.rupture_a_honorer; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.commande.rupture_a_honorer IS 'Regle (a) du paiement : stock devenu insuffisant au moment du paiement confirme. La commande est payee, le garage doit honorer la rupture hors ligne. Le detail des lignes concernees est journalise a la detection.';


--
-- Name: COLUMN commande.renonciation_vi53; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.commande.renonciation_vi53 IS 'Le client a renonce a son droit de retractation pour execution immediate du service (art. VI.53 CDE, F12). ETAT lu par F30 pour decider ; la preuve horodatee est la ligne consentement de type RENONCIATION_RETRACTATION. false pour toute commande de pieces et toute commande anterieure a F12.';


--
-- Name: commande_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.commande_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: commande_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.commande_id_seq OWNED BY public.commande.id;


--
-- Name: compteur_avoir; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.compteur_avoir (
    exercice smallint NOT NULL,
    dernier_numero integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(120),
    CONSTRAINT ck_compteur_avoir_numero CHECK ((dernier_numero >= 0))
);


--
-- Name: TABLE compteur_avoir; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.compteur_avoir IS 'Compteur transactionnel des notes de credit, une ligne par exercice comptable. Distinct de compteur_facture : un avoir a sa propre suite legale et ne consomme pas un numero de facture. Incremente sous SELECT ... FOR UPDATE dans la transaction d emission, pour la meme raison qu en V26 : un rollback annule l increment et la suite reste sans trou. Ne jamais remplacer par une sequence.';


--
-- Name: compteur_facture; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.compteur_facture (
    exercice smallint NOT NULL,
    dernier_numero integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(120),
    CONSTRAINT ck_compteur_facture_numero CHECK ((dernier_numero >= 0))
);


--
-- Name: TABLE compteur_facture; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.compteur_facture IS 'Compteur transactionnel des factures, une ligne par exercice comptable. Incremente sous SELECT ... FOR UPDATE dans la transaction d emission : un rollback annule l increment, ce qui garantit une numerotation sans trou (obligation legale). Ne jamais remplacer par une sequence, qui laisserait des trous par construction.';


--
-- Name: COLUMN compteur_facture.dernier_numero; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.compteur_facture.dernier_numero IS 'Dernier numero attribue pour cet exercice. La prochaine facture porte dernier_numero + 1.';


--
-- Name: consentement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.consentement (
    id bigint NOT NULL,
    utilisateur_id bigint NOT NULL,
    type_document character varying(30) NOT NULL,
    version_acceptee character varying(20) NOT NULL,
    accorde boolean NOT NULL,
    date_consentement timestamp with time zone DEFAULT now() NOT NULL,
    adresse_ip character varying(45),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    CONSTRAINT ck_consentement_type CHECK (((type_document)::text = ANY ((ARRAY['CGV'::character varying, 'POLITIQUE_CONFIDENTIALITE'::character varying, 'COOKIES'::character varying, 'NEWSLETTER'::character varying, 'COOKIES_ANALYTIQUE'::character varying, 'COOKIES_MARKETING'::character varying, 'RENONCIATION_RETRACTATION'::character varying])::text[])))
);


--
-- Name: TABLE consentement; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.consentement IS 'Preuve horodatee des consentements RGPD. Jamais supprimee : sert de preuve.';


--
-- Name: COLUMN consentement.type_document; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.consentement.type_document IS 'Document ou finalite auquel se rapporte le consentement. RENONCIATION_RETRACTATION : renonciation VI.53 pour un service pleinement execute (F12) — la PREUVE ; l etat lu par F30 est commande.renonciation_vi53.';


--
-- Name: consentement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.consentement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: consentement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.consentement_id_seq OWNED BY public.consentement.id;


--
-- Name: conversation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.conversation (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    membre_id bigint NOT NULL,
    intervention_id bigint,
    sujet character varying(150) NOT NULL,
    cloturee boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120)
);


--
-- Name: COLUMN conversation.intervention_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.conversation.intervention_id IS 'Nullable : une conversation peut porter sur un sujet general.';


--
-- Name: conversation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.conversation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: conversation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.conversation_id_seq OWNED BY public.conversation.id;


--
-- Name: demande_annulation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.demande_annulation (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    commande_id bigint NOT NULL,
    statut character varying(20) DEFAULT 'EN_ATTENTE'::character varying NOT NULL,
    motif_membre text,
    motif_decision text,
    decide_par bigint,
    decide_le timestamp with time zone,
    avoir_id bigint,
    date_demande timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_demande_annulation_avoir CHECK (((((statut)::text = 'VALIDEE'::text) AND (avoir_id IS NOT NULL)) OR (((statut)::text <> 'VALIDEE'::text) AND (avoir_id IS NULL)))),
    CONSTRAINT ck_demande_annulation_decision CHECK (((((statut)::text = 'EN_ATTENTE'::text) AND (decide_par IS NULL) AND (decide_le IS NULL)) OR (((statut)::text <> 'EN_ATTENTE'::text) AND (decide_par IS NOT NULL) AND (decide_le IS NOT NULL)))),
    CONSTRAINT ck_demande_annulation_statut CHECK (((statut)::text = ANY ((ARRAY['EN_ATTENTE'::character varying, 'VALIDEE'::character varying, 'REFUSEE'::character varying])::text[])))
);


--
-- Name: TABLE demande_annulation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.demande_annulation IS 'Demande de retractation d une commande de marchandises (F30, RM-23), tranchee par l administrateur. Le controle automatique porte ce que le systeme sait (proprietaire, commande payee, delai de 14 jours) ; l etat physique de la piece releve de l atelier, d ou la validation humaine. Perimetre V1 : annulation totale de la commande.';


--
-- Name: COLUMN demande_annulation.motif_membre; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.demande_annulation.motif_membre IS 'Facultatif : le droit de retractation est inconditionnel, le consommateur n a pas a se justifier (CDE, art. VI.47). Recueilli a titre d information pour le garage.';


--
-- Name: COLUMN demande_annulation.motif_decision; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.demande_annulation.motif_decision IS 'Renseigne surtout au refus : le consommateur doit savoir sur quel constat le garage s est appuye pour lui opposer une exception.';


--
-- Name: COLUMN demande_annulation.deleted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.demande_annulation.deleted_at IS 'Inerte en V1 : une demande de retractation est une trace juridique, aucun chemin ne la supprime. Colonne presente par uniformite avec les entites metier du projet.';


--
-- Name: demande_annulation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.demande_annulation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: demande_annulation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.demande_annulation_id_seq OWNED BY public.demande_annulation.id;


--
-- Name: facture; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.facture (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    numero character varying(20) NOT NULL,
    exercice smallint NOT NULL,
    sequence_annuelle integer NOT NULL,
    commande_id bigint,
    intervention_id bigint,
    membre_id bigint NOT NULL,
    montant_htva numeric(10,2) NOT NULL,
    montant_tva numeric(10,2) NOT NULL,
    montant_tvac numeric(10,2) NOT NULL,
    taux_tva_applique numeric(5,2),
    date_emission timestamp with time zone DEFAULT now() NOT NULL,
    date_echeance date,
    chemin_pdf character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    CONSTRAINT ck_facture_coherence CHECK ((montant_tvac = (montant_htva + montant_tva))),
    CONSTRAINT ck_facture_montants CHECK (((montant_htva >= (0)::numeric) AND (montant_tva >= (0)::numeric) AND (montant_tvac >= (0)::numeric))),
    CONSTRAINT ck_facture_origine_unique CHECK ((((commande_id IS NOT NULL) AND (intervention_id IS NULL)) OR ((commande_id IS NULL) AND (intervention_id IS NOT NULL)))),
    CONSTRAINT ck_facture_sequence CHECK ((sequence_annuelle > 0))
);


--
-- Name: TABLE facture; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.facture IS 'Document comptable immuable. Numerotation continue par exercice, sans trou.';


--
-- Name: COLUMN facture.taux_tva_applique; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.facture.taux_tva_applique IS 'Taux unique de la facture, ou NULL si elle melange plusieurs taux : la ventilation par taux est alors portee par le document, calculee des lignes de la source.';


--
-- Name: facture_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.facture_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: facture_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.facture_id_seq OWNED BY public.facture.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: historique_modification_catalogue; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historique_modification_catalogue (
    id bigint NOT NULL,
    type_entite character varying(20) NOT NULL,
    entite_id bigint NOT NULL,
    champ_modifie character varying(60) NOT NULL,
    valeur_avant text,
    valeur_apres text,
    horodatage timestamp with time zone NOT NULL,
    auteur_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    CONSTRAINT ck_histo_catalogue_type CHECK (((type_entite)::text = ANY ((ARRAY['PRESTATION'::character varying, 'PIECE'::character varying])::text[])))
);


--
-- Name: TABLE historique_modification_catalogue; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.historique_modification_catalogue IS 'Journal append-only des modifications du catalogue (A2, A5). Une ligne par champ reellement modifie, ecrite dans la transaction de la modification.';


--
-- Name: COLUMN historique_modification_catalogue.entite_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.historique_modification_catalogue.entite_id IS 'Identifiant de la prestation (service.id) ou de la piece (piece.id) selon type_entite. Volontairement sans FK : colonne polymorphe, et la trace survit a la suppression A3/A6.';


--
-- Name: COLUMN historique_modification_catalogue.champ_modifie; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.historique_modification_catalogue.champ_modifie IS 'Nom technique du champ modifie, stable dans le temps ; la traduction est affaire de presentation.';


--
-- Name: COLUMN historique_modification_catalogue.horodatage; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.historique_modification_catalogue.horodatage IS 'Instant metier de la modification (horloge applicative injectee), distinct de l audit created_at.';


--
-- Name: historique_modification_catalogue_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.historique_modification_catalogue_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: historique_modification_catalogue_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.historique_modification_catalogue_id_seq OWNED BY public.historique_modification_catalogue.id;


--
-- Name: historique_statut_intervention; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historique_statut_intervention (
    id bigint NOT NULL,
    intervention_id bigint NOT NULL,
    statut_avant character varying(30),
    statut_apres character varying(30) NOT NULL,
    horodatage timestamp with time zone NOT NULL,
    auteur_id bigint,
    motif character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    CONSTRAINT ck_histo_statut_apres CHECK (((statut_apres)::text = ANY ((ARRAY['PLANIFIEE'::character varying, 'EN_COURS'::character varying, 'SUSPENDUE'::character varying, 'ATTENTE_VALIDATION_MEMBRE'::character varying, 'TERMINEE'::character varying, 'ANNULEE'::character varying])::text[]))),
    CONSTRAINT ck_histo_statut_avant CHECK (((statut_avant)::text = ANY ((ARRAY['PLANIFIEE'::character varying, 'EN_COURS'::character varying, 'SUSPENDUE'::character varying, 'ATTENTE_VALIDATION_MEMBRE'::character varying, 'TERMINEE'::character varying, 'ANNULEE'::character varying])::text[])))
);


--
-- Name: TABLE historique_statut_intervention; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.historique_statut_intervention IS 'Journal append-only des transitions de statut d une intervention (F17). Une ligne par transition, ecrite dans la transaction de la transition.';


--
-- Name: COLUMN historique_statut_intervention.statut_avant; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.historique_statut_intervention.statut_avant IS 'NULL pour la ligne de creation : le dossier n a pas d etat anterieur.';


--
-- Name: COLUMN historique_statut_intervention.horodatage; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.historique_statut_intervention.horodatage IS 'Instant metier de la transition (horloge applicative injectee), distinct de l audit created_at.';


--
-- Name: historique_statut_intervention_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.historique_statut_intervention_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: historique_statut_intervention_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.historique_statut_intervention_id_seq OWNED BY public.historique_statut_intervention.id;


--
-- Name: indisponibilite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.indisponibilite (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    poste_id bigint,
    debut timestamp with time zone NOT NULL,
    fin timestamp with time zone NOT NULL,
    motif character varying(200) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_indispo_intervalle CHECK ((fin > debut))
);


--
-- Name: indisponibilite_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.indisponibilite_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: indisponibilite_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.indisponibilite_id_seq OWNED BY public.indisponibilite.id;


--
-- Name: intervention; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.intervention (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    numero character varying(20) NOT NULL,
    rdv_id bigint,
    vehicule_id bigint NOT NULL,
    statut character varying(30) DEFAULT 'PLANIFIEE'::character varying NOT NULL,
    diagnostic text,
    montant_devis_htva numeric(10,2) NOT NULL,
    montant_reel_htva numeric(10,2),
    depassement_notifie boolean DEFAULT false NOT NULL,
    accord_client boolean,
    date_accord_client timestamp with time zone,
    debut_reel timestamp with time zone,
    fin_reelle timestamp with time zone,
    kilometrage_releve integer,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    commentaire_admin text,
    commande_id bigint,
    CONSTRAINT ck_intervention_bornes CHECK (((fin_reelle IS NULL) OR (debut_reel IS NULL) OR (fin_reelle >= debut_reel))),
    CONSTRAINT ck_intervention_kilometrage CHECK (((kilometrage_releve IS NULL) OR (kilometrage_releve >= 0))),
    CONSTRAINT ck_intervention_montants CHECK ((((montant_devis_htva IS NULL) OR (montant_devis_htva >= (0)::numeric)) AND ((montant_reel_htva IS NULL) OR (montant_reel_htva >= (0)::numeric)))),
    CONSTRAINT ck_intervention_origine_unique CHECK ((num_nonnulls(rdv_id, commande_id) <= 1)),
    CONSTRAINT ck_intervention_statut CHECK (((statut)::text = ANY ((ARRAY['PLANIFIEE'::character varying, 'EN_COURS'::character varying, 'SUSPENDUE'::character varying, 'ATTENTE_VALIDATION_MEMBRE'::character varying, 'TERMINEE'::character varying, 'ANNULEE'::character varying])::text[])))
);


--
-- Name: TABLE intervention; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.intervention IS 'Travail effectue sur un vehicule. Peut naitre d un rendez-vous ou d une entree directe.';


--
-- Name: COLUMN intervention.montant_devis_htva; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.intervention.montant_devis_htva IS 'Devis initial HTVA fige a la creation depuis les lignes du rendez-vous. Reference de comparaison de RM-15 : le seuil vaut ce montant majore de dix pour cent. Obligatoire depuis V21 — une intervention sans devis rendrait la regle incalculable.';


--
-- Name: COLUMN intervention.depassement_notifie; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.intervention.depassement_notifie IS 'Regle RM-15 : un depassement de plus de dix pour cent du devis exige un accord expres du client avant poursuite.';


--
-- Name: COLUMN intervention.version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.intervention.version IS 'Verrouillage optimiste : deux mecaniciens ne peuvent pas modifier la meme intervention simultanement.';


--
-- Name: COLUMN intervention.commentaire_admin; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.intervention.commentaire_admin IS 'Note du garage visible par le client dans le suivi de l intervention (F17). Distinct de diagnostic (interne au garage).';


--
-- Name: COLUMN intervention.commande_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.intervention.commande_id IS 'Commande de services payee dont cette intervention execute une ligne (F12-b). Exclusif avec rdv_id ; les deux nuls = entree directe au garage. Sert a F30 pour savoir si un service sous renonciation VI.53 est pleinement execute.';


--
-- Name: intervention_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.intervention_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: intervention_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.intervention_id_seq OWNED BY public.intervention.id;


--
-- Name: ligne_intervention; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ligne_intervention (
    id bigint NOT NULL,
    intervention_id bigint NOT NULL,
    service_id bigint,
    piece_id bigint,
    libelle_fige character varying(150) NOT NULL,
    quantite smallint NOT NULL,
    prix_unitaire_htva numeric(10,2) NOT NULL,
    taux_tva numeric(5,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    ajoutee_en_cours boolean DEFAULT false NOT NULL,
    accord_membre boolean,
    CONSTRAINT ck_ligne_interv_accord CHECK ((ajoutee_en_cours OR (accord_membre IS NULL))),
    CONSTRAINT ck_ligne_interv_article CHECK ((((service_id IS NOT NULL) AND (piece_id IS NULL)) OR ((service_id IS NULL) AND (piece_id IS NOT NULL)))),
    CONSTRAINT ck_ligne_interv_prix CHECK ((prix_unitaire_htva >= (0)::numeric)),
    CONSTRAINT ck_ligne_interv_quantite CHECK ((quantite > 0))
);


--
-- Name: COLUMN ligne_intervention.ajoutee_en_cours; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ligne_intervention.ajoutee_en_cours IS 'FALSE : ligne issue du rendez-vous, elle compose le devis initial accepte par le membre a la reservation. TRUE : ajoutee par le garage pendant l intervention (RM-15).';


--
-- Name: COLUMN ligne_intervention.accord_membre; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ligne_intervention.accord_membre IS 'Reponse du membre sur une ligne ajoutee en cours d intervention (RM-15). NULL : aucune reponse — ligne du devis initial si ajoutee_en_cours vaut false, ajout en attente de validation sinon. TRUE : accepte, entre dans le total facturable. FALSE : refuse, conserve au dossier comme trace du defaut constate, exclu du total, non execute.';


--
-- Name: ligne_intervention_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ligne_intervention_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ligne_intervention_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ligne_intervention_id_seq OWNED BY public.ligne_intervention.id;


--
-- Name: ligne_panier; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ligne_panier (
    id bigint NOT NULL,
    panier_id bigint,
    commande_id bigint,
    service_id bigint,
    piece_id bigint,
    libelle_fige character varying(150) NOT NULL,
    quantite smallint NOT NULL,
    prix_unitaire_htva numeric(10,2) NOT NULL,
    taux_tva numeric(5,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    CONSTRAINT ck_ligne_article_unique CHECK ((((service_id IS NOT NULL) AND (piece_id IS NULL)) OR ((service_id IS NULL) AND (piece_id IS NOT NULL)))),
    CONSTRAINT ck_ligne_prix CHECK ((prix_unitaire_htva >= (0)::numeric)),
    CONSTRAINT ck_ligne_quantite CHECK ((quantite > 0)),
    CONSTRAINT ck_ligne_rattachement_unique CHECK ((((panier_id IS NOT NULL) AND (commande_id IS NULL)) OR ((panier_id IS NULL) AND (commande_id IS NOT NULL))))
);


--
-- Name: COLUMN ligne_panier.libelle_fige; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ligne_panier.libelle_fige IS 'Libelle recopie a la commande : la facture ne change pas si le catalogue evolue.';


--
-- Name: CONSTRAINT ck_ligne_rattachement_unique ON ligne_panier; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_ligne_rattachement_unique ON public.ligne_panier IS 'Une ligne appartient soit a un panier, soit a une commande, jamais aux deux.';


--
-- Name: ligne_panier_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ligne_panier_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ligne_panier_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ligne_panier_id_seq OWNED BY public.ligne_panier.id;


--
-- Name: message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.message (
    id bigint NOT NULL,
    conversation_id bigint NOT NULL,
    expediteur_id bigint NOT NULL,
    role_expediteur character varying(20) NOT NULL,
    corps text NOT NULL,
    lu boolean DEFAULT false NOT NULL,
    date_envoi timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_message_role CHECK (((role_expediteur)::text = ANY ((ARRAY['MEMBRE'::character varying, 'ADMINISTRATEUR'::character varying])::text[])))
);


--
-- Name: message_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: message_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.message_id_seq OWNED BY public.message.id;


--
-- Name: notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification (
    id bigint NOT NULL,
    membre_id bigint NOT NULL,
    type character varying(40) NOT NULL,
    titre character varying(150) NOT NULL,
    corps text NOT NULL,
    statut character varying(20) DEFAULT 'NON_LUE'::character varying NOT NULL,
    canal character varying(20) DEFAULT 'APPLICATION'::character varying NOT NULL,
    date_envoi timestamp with time zone,
    date_lecture timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_notification_canal CHECK (((canal)::text = ANY ((ARRAY['APPLICATION'::character varying, 'EMAIL'::character varying, 'LES_DEUX'::character varying])::text[]))),
    CONSTRAINT ck_notification_statut CHECK (((statut)::text = ANY ((ARRAY['NON_LUE'::character varying, 'LUE'::character varying, 'ARCHIVEE'::character varying])::text[])))
);


--
-- Name: notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_id_seq OWNED BY public.notification.id;


--
-- Name: paiement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.paiement (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    commande_id bigint,
    reference_mollie character varying(64),
    cle_idempotence character varying(64) NOT NULL,
    montant numeric(10,2) NOT NULL,
    devise character varying(3) DEFAULT 'EUR'::character varying NOT NULL,
    methode character varying(30),
    statut character varying(25) DEFAULT 'INITIE'::character varying NOT NULL,
    date_initiation timestamp with time zone DEFAULT now() NOT NULL,
    date_finalisation timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    version bigint DEFAULT 0 NOT NULL,
    reference_remboursement character varying(64),
    CONSTRAINT ck_paiement_montant CHECK ((montant > (0)::numeric)),
    CONSTRAINT ck_paiement_statut CHECK (((statut)::text = ANY ((ARRAY['INITIE'::character varying, 'EN_COURS'::character varying, 'REUSSI'::character varying, 'ECHOUE'::character varying, 'EXPIRE'::character varying, 'REMBOURSE'::character varying])::text[])))
);


--
-- Name: COLUMN paiement.commande_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.paiement.commande_id IS 'Nullable : un paiement peut aussi couvrir une reservation de parking.';


--
-- Name: COLUMN paiement.cle_idempotence; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.paiement.cle_idempotence IS 'Empeche le double debit si la requete est rejouee.';


--
-- Name: COLUMN paiement.version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.paiement.version IS 'Verrouillage optimiste : un webhook et le job d expiration peuvent viser le meme paiement.';


--
-- Name: COLUMN paiement.reference_remboursement; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.paiement.reference_remboursement IS 'Identifiant du Refund chez le prestataire, contrepartie de reference_mollie. Seul point de rapprochement avec l extrait du prestataire en cas de contestation. La cle d idempotence du remboursement, elle, est derivee de paiement.reference et n a pas besoin d etre stockee.';


--
-- Name: paiement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.paiement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: paiement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.paiement_id_seq OWNED BY public.paiement.id;


--
-- Name: panier; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.panier (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    membre_id bigint NOT NULL,
    date_expiration timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120)
);


--
-- Name: panier_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.panier_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: panier_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.panier_id_seq OWNED BY public.panier.id;


--
-- Name: parametre_atelier; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.parametre_atelier (
    id smallint DEFAULT 1 NOT NULL,
    fuseau_horaire character varying(60) DEFAULT 'Europe/Brussels'::character varying NOT NULL,
    pas_minutes smallint DEFAULT 30 NOT NULL,
    tampon_minutes smallint DEFAULT 10 NOT NULL,
    delai_minimal_heures smallint DEFAULT 24 NOT NULL,
    horizon_jours smallint DEFAULT 60 NOT NULL,
    delai_annulation_heures smallint DEFAULT 24 NOT NULL,
    confirmation_automatique boolean DEFAULT false NOT NULL,
    max_rdv_en_attente_par_membre smallint DEFAULT 3 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(120),
    CONSTRAINT ck_param_delai_annulation CHECK (((delai_annulation_heures >= 0) AND (delai_annulation_heures <= 168))),
    CONSTRAINT ck_param_delai_minimal CHECK (((delai_minimal_heures >= 0) AND (delai_minimal_heures <= 168))),
    CONSTRAINT ck_param_horizon CHECK (((horizon_jours >= 1) AND (horizon_jours <= 365))),
    CONSTRAINT ck_param_ligne_unique CHECK ((id = 1)),
    CONSTRAINT ck_param_max_attente CHECK (((max_rdv_en_attente_par_membre >= 1) AND (max_rdv_en_attente_par_membre <= 20))),
    CONSTRAINT ck_param_pas CHECK ((pas_minutes = ANY (ARRAY[15, 30, 45, 60]))),
    CONSTRAINT ck_param_tampon CHECK (((tampon_minutes >= 0) AND (tampon_minutes <= 120)))
);


--
-- Name: COLUMN parametre_atelier.fuseau_horaire; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.parametre_atelier.fuseau_horaire IS 'Les plages d ouverture sont en heure locale ; ce fuseau sert a les projeter en instants UTC.';


--
-- Name: photo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.photo (
    id bigint NOT NULL,
    service_id bigint,
    piece_id bigint,
    chemin character varying(255) NOT NULL,
    texte_alt character varying(200) NOT NULL,
    ordre smallint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    intervention_id bigint,
    CONSTRAINT ck_photo_origine_unique CHECK ((num_nonnulls(service_id, piece_id, intervention_id) = 1))
);


--
-- Name: COLUMN photo.texte_alt; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.photo.texte_alt IS 'Obligatoire : exigence WCAG 2.1 niveau AA, critere 1.1.1.';


--
-- Name: COLUMN photo.intervention_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.photo.intervention_id IS 'Photos avant / apres d une intervention (BL-9). Exclusif avec service_id et piece_id.';


--
-- Name: photo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.photo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: photo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.photo_id_seq OWNED BY public.photo.id;


--
-- Name: piece; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.piece (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    categorie_id bigint NOT NULL,
    reference_fabricant character varying(60) NOT NULL,
    libelle character varying(150) NOT NULL,
    description text,
    marque character varying(80),
    prix_htva numeric(10,2) NOT NULL,
    taux_tva numeric(5,2) DEFAULT 21.00 NOT NULL,
    quantite_stock integer DEFAULT 0 NOT NULL,
    seuil_alerte integer DEFAULT 0 NOT NULL,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_piece_prix CHECK ((prix_htva >= (0)::numeric)),
    CONSTRAINT ck_piece_seuil CHECK ((seuil_alerte >= 0)),
    CONSTRAINT ck_piece_stock CHECK ((quantite_stock >= 0))
);


--
-- Name: piece_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.piece_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: piece_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.piece_id_seq OWNED BY public.piece.id;


--
-- Name: place_parking; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.place_parking (
    id bigint NOT NULL,
    numero character varying(10) NOT NULL,
    type character varying(20) DEFAULT 'STANDARD'::character varying NOT NULL,
    tarif_jour_htva numeric(10,2) NOT NULL,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_place_parking_tarif CHECK ((tarif_jour_htva >= (0)::numeric)),
    CONSTRAINT ck_place_parking_type CHECK (((type)::text = ANY ((ARRAY['STANDARD'::character varying, 'COUVERTE'::character varying, 'PMR'::character varying, 'GRANDE'::character varying])::text[])))
);


--
-- Name: COLUMN place_parking.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.place_parking.type IS 'PMR : place reservee aux personnes a mobilite reduite.';


--
-- Name: place_parking_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.place_parking_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: place_parking_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.place_parking_id_seq OWNED BY public.place_parking.id;


--
-- Name: plage_ouverture; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plage_ouverture (
    id bigint NOT NULL,
    jour_semaine smallint NOT NULL,
    heure_debut time without time zone NOT NULL,
    heure_fin time without time zone NOT NULL,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_plage_heures CHECK ((heure_fin > heure_debut)),
    CONSTRAINT ck_plage_jour CHECK (((jour_semaine >= 1) AND (jour_semaine <= 7)))
);


--
-- Name: COLUMN plage_ouverture.jour_semaine; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plage_ouverture.jour_semaine IS '1 = lundi ... 7 = dimanche, norme ISO 8601.';


--
-- Name: plage_ouverture_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.plage_ouverture_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: plage_ouverture_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.plage_ouverture_id_seq OWNED BY public.plage_ouverture.id;


--
-- Name: poste_atelier; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.poste_atelier (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    libelle character varying(80) NOT NULL,
    description text,
    ordre smallint DEFAULT 0 NOT NULL,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120)
);


--
-- Name: TABLE poste_atelier; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.poste_atelier IS 'Ressource planifiable (pont, baie). Un mecanicien ou des competences pourront y etre rattaches.';


--
-- Name: poste_atelier_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.poste_atelier_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: poste_atelier_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.poste_atelier_id_seq OWNED BY public.poste_atelier.id;


--
-- Name: rdv; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rdv (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    numero character varying(20) NOT NULL,
    membre_id bigint NOT NULL,
    vehicule_id bigint NOT NULL,
    statut character varying(25) DEFAULT 'EN_ATTENTE'::character varying NOT NULL,
    commentaire text,
    motif_refus text,
    date_annulation timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    debut timestamp with time zone NOT NULL,
    fin timestamp with time zone NOT NULL,
    poste_id bigint NOT NULL,
    CONSTRAINT ck_rdv_intervalle CHECK ((fin > debut)),
    CONSTRAINT ck_rdv_statut CHECK (((statut)::text = ANY ((ARRAY['EN_ATTENTE'::character varying, 'CONFIRME'::character varying, 'REFUSE'::character varying, 'ANNULE'::character varying, 'HONORE'::character varying, 'ABSENT'::character varying])::text[])))
);


--
-- Name: rdv_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rdv_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rdv_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rdv_id_seq OWNED BY public.rdv.id;


--
-- Name: rdv_numero_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rdv_numero_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rdv_service; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rdv_service (
    id bigint NOT NULL,
    rdv_id bigint NOT NULL,
    service_id bigint NOT NULL,
    quantite smallint DEFAULT 1 NOT NULL,
    prix_unitaire_htva numeric(10,2) NOT NULL,
    taux_tva numeric(5,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    CONSTRAINT ck_rdv_service_prix CHECK ((prix_unitaire_htva >= (0)::numeric)),
    CONSTRAINT ck_rdv_service_qte CHECK ((quantite > 0))
);


--
-- Name: COLUMN rdv_service.prix_unitaire_htva; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.rdv_service.prix_unitaire_htva IS 'Prix fige a la reservation : le catalogue peut evoluer ensuite.';


--
-- Name: rdv_service_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rdv_service_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rdv_service_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rdv_service_id_seq OWNED BY public.rdv_service.id;


--
-- Name: reservation_parking; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reservation_parking (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    numero character varying(20) NOT NULL,
    membre_id bigint NOT NULL,
    vehicule_id bigint NOT NULL,
    place_id bigint NOT NULL,
    paiement_id bigint,
    date_debut date NOT NULL,
    date_fin date NOT NULL,
    montant_htva numeric(10,2) NOT NULL,
    montant_tvac numeric(10,2) NOT NULL,
    statut character varying(25) DEFAULT 'EN_ATTENTE_PAIEMENT'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_resa_parking_dates CHECK ((date_fin >= date_debut)),
    CONSTRAINT ck_resa_parking_montant CHECK (((montant_htva >= (0)::numeric) AND (montant_tvac >= (0)::numeric))),
    CONSTRAINT ck_resa_parking_statut CHECK (((statut)::text = ANY ((ARRAY['EN_ATTENTE_PAIEMENT'::character varying, 'CONFIRMEE'::character varying, 'EN_COURS'::character varying, 'TERMINEE'::character varying, 'ANNULEE'::character varying])::text[])))
);


--
-- Name: COLUMN reservation_parking.paiement_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reservation_parking.paiement_id IS 'Nullable : la place est reservee avant que le paiement n aboutisse.';


--
-- Name: reservation_parking_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reservation_parking_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reservation_parking_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reservation_parking_id_seq OWNED BY public.reservation_parking.id;


--
-- Name: seq_numero_avoir; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seq_numero_avoir
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: SEQUENCE seq_numero_avoir; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON SEQUENCE public.seq_numero_avoir IS 'INUTILISEE depuis V27. Conservee pour ne pas reecrire l historique des migrations. Une sequence n est pas transactionnelle : elle laisse des trous au rollback, ce qui est interdit pour un document rectificatif au meme titre que pour une facture. Voir la table compteur_avoir.';


--
-- Name: seq_numero_commande; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seq_numero_commande
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seq_numero_facture; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seq_numero_facture
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: SEQUENCE seq_numero_facture; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON SEQUENCE public.seq_numero_facture IS 'INUTILISEE depuis V26. Conservee pour ne pas reecrire l historique des migrations. Une sequence n est pas transactionnelle : elle laisse des trous au rollback, ce qui est interdit pour un numero de facture. Voir la table compteur_facture.';


--
-- Name: seq_numero_intervention; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seq_numero_intervention
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seq_numero_parking; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seq_numero_parking
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seq_numero_rdv; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seq_numero_rdv
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: service; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    categorie_id bigint NOT NULL,
    code character varying(40) NOT NULL,
    libelle character varying(150) NOT NULL,
    description text,
    prix_htva numeric(10,2) NOT NULL,
    taux_tva numeric(5,2) DEFAULT 21.00 NOT NULL,
    duree_minutes integer NOT NULL,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_service_duree CHECK ((duree_minutes > 0)),
    CONSTRAINT ck_service_prix CHECK ((prix_htva >= (0)::numeric)),
    CONSTRAINT ck_service_tva CHECK (((taux_tva >= (0)::numeric) AND (taux_tva <= (100)::numeric)))
);


--
-- Name: COLUMN service.duree_minutes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.service.duree_minutes IS 'Duree standard de la prestation, utilisee pour calculer les creneaux.';


--
-- Name: service_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.service_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: service_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.service_id_seq OWNED BY public.service.id;


--
-- Name: utilisateur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.utilisateur (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    type_utilisateur character varying(20) NOT NULL,
    email character varying(180) NOT NULL,
    mot_de_passe_hache character varying(60) NOT NULL,
    nom character varying(80) NOT NULL,
    prenom character varying(80) NOT NULL,
    telephone character varying(30),
    rue character varying(150),
    numero_rue character varying(15),
    code_postal character varying(10),
    localite character varying(100),
    pays character varying(60) DEFAULT 'Belgique'::character varying NOT NULL,
    langue character varying(2) DEFAULT 'fr'::character varying NOT NULL,
    statut character varying(30) DEFAULT 'EN_ATTENTE_VALIDATION'::character varying NOT NULL,
    email_verifie boolean DEFAULT false NOT NULL,
    jeton_verification character varying(64),
    jeton_expiration timestamp with time zone,
    derniere_connexion timestamp with time zone,
    tentatives_echouees smallint DEFAULT 0 NOT NULL,
    verrouille_jusqu_a timestamp with time zone,
    fonction character varying(80),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    anonymise_le timestamp with time zone,
    CONSTRAINT ck_utilisateur_langue CHECK (((langue)::text = ANY ((ARRAY['fr'::character varying, 'nl'::character varying, 'en'::character varying])::text[]))),
    CONSTRAINT ck_utilisateur_statut CHECK (((statut)::text = ANY ((ARRAY['EN_ATTENTE_VALIDATION'::character varying, 'ACTIF'::character varying, 'SUSPENDU'::character varying, 'SUPPRIME'::character varying])::text[]))),
    CONSTRAINT ck_utilisateur_tentatives CHECK ((tentatives_echouees >= 0)),
    CONSTRAINT ck_utilisateur_type CHECK (((type_utilisateur)::text = ANY ((ARRAY['MEMBRE'::character varying, 'ADMINISTRATEUR'::character varying])::text[])))
);


--
-- Name: TABLE utilisateur; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.utilisateur IS 'Comptes de la plateforme. Heritage a table unique.';


--
-- Name: COLUMN utilisateur.mot_de_passe_hache; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.utilisateur.mot_de_passe_hache IS 'Empreinte BCrypt facteur 12, longueur fixe de 60 caracteres.';


--
-- Name: COLUMN utilisateur.anonymise_le; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.utilisateur.anonymise_le IS 'Horodatage de l anonymisation du compte (F23, art. 17 RGPD). Marqueur d etat et NON suppression logique : deleted_at doit rester vide, sans quoi le SQLRestriction de l entite masquerait la ligne et les factures conservees ne pourraient plus resoudre leur titulaire. La ligne survit, videe de toute donnee personnelle.';


--
-- Name: utilisateur_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.utilisateur_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: utilisateur_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.utilisateur_id_seq OWNED BY public.utilisateur.id;


--
-- Name: vehicule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vehicule (
    id bigint NOT NULL,
    reference uuid DEFAULT gen_random_uuid() NOT NULL,
    membre_id bigint NOT NULL,
    plaque character varying(15) NOT NULL,
    marque character varying(60) NOT NULL,
    modele character varying(80) NOT NULL,
    motorisation character varying(20) NOT NULL,
    annee smallint,
    kilometrage integer,
    numero_chassis character varying(20),
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    deleted_at timestamp with time zone,
    deleted_by character varying(120),
    CONSTRAINT ck_vehicule_annee CHECK (((annee IS NULL) OR ((annee >= 1900) AND (annee <= 2100)))),
    CONSTRAINT ck_vehicule_kilometrage CHECK (((kilometrage IS NULL) OR (kilometrage >= 0))),
    CONSTRAINT ck_vehicule_motorisation CHECK (((motorisation)::text = ANY ((ARRAY['ESSENCE'::character varying, 'DIESEL'::character varying, 'HYBRIDE'::character varying, 'ELECTRIQUE'::character varying, 'GPL'::character varying, 'AUTRE'::character varying])::text[])))
);


--
-- Name: COLUMN vehicule.plaque; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.vehicule.plaque IS 'Unique parmi les vehicules non supprimes : une plaque peut etre reattribuee.';


--
-- Name: vehicule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.vehicule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: vehicule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.vehicule_id_seq OWNED BY public.vehicule.id;


--
-- Name: version_document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.version_document (
    id bigint NOT NULL,
    type_document character varying(30) NOT NULL,
    version character varying(20) NOT NULL,
    langue character varying(2) NOT NULL,
    date_effet timestamp with time zone NOT NULL,
    contenu text NOT NULL,
    empreinte character varying(64) NOT NULL,
    actif boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by character varying(120),
    updated_by character varying(120),
    CONSTRAINT ck_version_document_contenu_non_vide CHECK ((length(btrim(contenu)) > 0)),
    CONSTRAINT ck_version_document_empreinte CHECK (((empreinte)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_version_document_langue CHECK (((langue)::text = ANY ((ARRAY['fr'::character varying, 'nl'::character varying, 'en'::character varying])::text[]))),
    CONSTRAINT ck_version_document_type CHECK (((type_document)::text = ANY ((ARRAY['CGV'::character varying, 'COOKIES'::character varying, 'RENONCIATION_RETRACTATION'::character varying])::text[])))
);


--
-- Name: TABLE version_document; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.version_document IS 'Texte engageant GELE d une version de document, une ligne par langue (F24). Archive append-only : une nouvelle redaction s ajoute, elle ne se modifie pas. consentement.version_acceptee resout vers la colonne version.';


--
-- Name: COLUMN version_document.date_effet; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.version_document.date_effet IS 'Entree en vigueur. La version en vigueur est la plus recente dont date_effet est passee : une publication peut donc etre datee a l avance, ce que la pratique juridique exige quand un changement de conditions doit etre annonce.';


--
-- Name: COLUMN version_document.contenu; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.version_document.contenu IS 'Texte tel qu il a ete presente, dans cette langue. N inclut PAS l identite du garage ni le registre des traitements, lus en configuration a chaque rendu : un changement d adresse n invalide pas un consentement.';


--
-- Name: COLUMN version_document.empreinte; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.version_document.empreinte IS 'SHA-256 hexadecimal du contenu. Compare a chaque build au texte reellement presente par l application : une clause modifiee sans nouvelle version casse la build au lieu de deriver en silence.';


--
-- Name: COLUMN version_document.actif; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.version_document.actif IS 'false retire la version du jeu resolvable sans la supprimer — une preuve qui la designe doit continuer a la resoudre.';


--
-- Name: version_document_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.version_document_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: version_document_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.version_document_id_seq OWNED BY public.version_document.id;


--
-- Name: avis id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avis ALTER COLUMN id SET DEFAULT nextval('public.avis_id_seq'::regclass);


--
-- Name: avoir id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avoir ALTER COLUMN id SET DEFAULT nextval('public.avoir_id_seq'::regclass);


--
-- Name: categorie id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categorie ALTER COLUMN id SET DEFAULT nextval('public.categorie_id_seq'::regclass);


--
-- Name: clef_api id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clef_api ALTER COLUMN id SET DEFAULT nextval('public.clef_api_id_seq'::regclass);


--
-- Name: commande id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commande ALTER COLUMN id SET DEFAULT nextval('public.commande_id_seq'::regclass);


--
-- Name: consentement id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consentement ALTER COLUMN id SET DEFAULT nextval('public.consentement_id_seq'::regclass);


--
-- Name: conversation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation ALTER COLUMN id SET DEFAULT nextval('public.conversation_id_seq'::regclass);


--
-- Name: demande_annulation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demande_annulation ALTER COLUMN id SET DEFAULT nextval('public.demande_annulation_id_seq'::regclass);


--
-- Name: facture id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facture ALTER COLUMN id SET DEFAULT nextval('public.facture_id_seq'::regclass);


--
-- Name: historique_modification_catalogue id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_modification_catalogue ALTER COLUMN id SET DEFAULT nextval('public.historique_modification_catalogue_id_seq'::regclass);


--
-- Name: historique_statut_intervention id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_statut_intervention ALTER COLUMN id SET DEFAULT nextval('public.historique_statut_intervention_id_seq'::regclass);


--
-- Name: indisponibilite id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indisponibilite ALTER COLUMN id SET DEFAULT nextval('public.indisponibilite_id_seq'::regclass);


--
-- Name: intervention id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intervention ALTER COLUMN id SET DEFAULT nextval('public.intervention_id_seq'::regclass);


--
-- Name: ligne_intervention id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ligne_intervention ALTER COLUMN id SET DEFAULT nextval('public.ligne_intervention_id_seq'::regclass);


--
-- Name: ligne_panier id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ligne_panier ALTER COLUMN id SET DEFAULT nextval('public.ligne_panier_id_seq'::regclass);


--
-- Name: message id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message ALTER COLUMN id SET DEFAULT nextval('public.message_id_seq'::regclass);


--
-- Name: notification id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification ALTER COLUMN id SET DEFAULT nextval('public.notification_id_seq'::regclass);


--
-- Name: paiement id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.paiement ALTER COLUMN id SET DEFAULT nextval('public.paiement_id_seq'::regclass);


--
-- Name: panier id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.panier ALTER COLUMN id SET DEFAULT nextval('public.panier_id_seq'::regclass);


--
-- Name: photo id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.photo ALTER COLUMN id SET DEFAULT nextval('public.photo_id_seq'::regclass);


--
-- Name: piece id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.piece ALTER COLUMN id SET DEFAULT nextval('public.piece_id_seq'::regclass);


--
-- Name: place_parking id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_parking ALTER COLUMN id SET DEFAULT nextval('public.place_parking_id_seq'::regclass);


--
-- Name: plage_ouverture id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plage_ouverture ALTER COLUMN id SET DEFAULT nextval('public.plage_ouverture_id_seq'::regclass);


--
-- Name: poste_atelier id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.poste_atelier ALTER COLUMN id SET DEFAULT nextval('public.poste_atelier_id_seq'::regclass);


--
-- Name: rdv id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv ALTER COLUMN id SET DEFAULT nextval('public.rdv_id_seq'::regclass);


--
-- Name: rdv_service id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv_service ALTER COLUMN id SET DEFAULT nextval('public.rdv_service_id_seq'::regclass);


--
-- Name: reservation_parking id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_parking ALTER COLUMN id SET DEFAULT nextval('public.reservation_parking_id_seq'::regclass);


--
-- Name: service id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service ALTER COLUMN id SET DEFAULT nextval('public.service_id_seq'::regclass);


--
-- Name: utilisateur id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utilisateur ALTER COLUMN id SET DEFAULT nextval('public.utilisateur_id_seq'::regclass);


--
-- Name: vehicule id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicule ALTER COLUMN id SET DEFAULT nextval('public.vehicule_id_seq'::regclass);


--
-- Name: version_document id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.version_document ALTER COLUMN id SET DEFAULT nextval('public.version_document_id_seq'::regclass);


--
-- Data for Name: avis; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.avis (id, reference, membre_id, intervention_id, note, commentaire, publie, signale, date_depot, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
1	fc4c4faf-dcec-4088-bd08-662d37aacc15	2	1	5	Revision faite dans les temps, explications claires sur les points controles.	t	f	2026-08-03 22:00:00+00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
\.


--
-- Data for Name: avoir; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.avoir (id, reference, numero, facture_id, montant_htva, montant_tva, montant_tvac, motif, date_emission, chemin_pdf, created_at, updated_at, created_by, updated_by) FROM stdin;
\.


--
-- Data for Name: categorie; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.categorie (id, code, libelle, type, description, ordre, actif, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
1	ENTRETIEN	Entretien courant	SERVICE	Vidange, filtres, revision periodique	1	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
2	FREINAGE	Freinage	SERVICE	Plaquettes, disques, liquide de frein	2	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
3	PNEUMATIQUE	Pneumatique	SERVICE	Montage, equilibrage, permutation	3	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
4	DIAGNOSTIC	Diagnostic electronique	SERVICE	Lecture de codes defaut, controle capteurs	4	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
5	CARROSSERIE	Carrosserie	SERVICE	Petites reparations et retouches	5	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
6	CT	Preparation controle technique	SERVICE	Verification avant passage au controle	6	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
7	P_FILTRES	Filtres	PIECE	Huile, air, habitacle, carburant	7	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
8	P_FREINAGE	Pieces de freinage	PIECE	Plaquettes, disques, etriers	8	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
9	P_PNEUS	Pneumatiques	PIECE	Ete, hiver, quatre saisons	9	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
10	P_BATTERIE	Batteries et electricite	PIECE	Batteries, bougies, ampoules	10	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
11	P_HUILE	Lubrifiants	PIECE	Huiles moteur et boite	11	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
\.


--
-- Data for Name: clef_api; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.clef_api (id, reference, libelle, clef_hachee, quota_minute, actif, date_expiration, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
\.


--
-- Data for Name: commande; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.commande (id, reference, numero, membre_id, statut, montant_htva, montant_tva, montant_tvac, date_commande, date_paiement, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by, motif_annulation, date_annulation, rupture_a_honorer, renonciation_vi53) FROM stdin;
1	d704f380-b7f9-41bf-a0e3-f4fffa14cdb5	CMD-2026-0001	2	PAYEE	190.40	39.98	230.38	2026-08-13 01:32:21.873264+00	2026-08-13 01:36:21.873264+00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	\N	\N	f	f
\.


--
-- Data for Name: compteur_avoir; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.compteur_avoir (exercice, dernier_numero, updated_at, updated_by) FROM stdin;
\.


--
-- Data for Name: compteur_facture; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.compteur_facture (exercice, dernier_numero, updated_at, updated_by) FROM stdin;
2026	1	2026-08-25 01:32:21.873264+00	\N
\.


--
-- Data for Name: consentement; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.consentement (id, utilisateur_id, type_document, version_acceptee, accorde, date_consentement, adresse_ip, created_at, updated_at, created_by, updated_by) FROM stdin;
1	2	CGV	CGV-2026-01	t	2026-07-16 01:32:21.873264+00	198.51.100.24	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	\N	\N
2	2	COOKIES_ANALYTIQUE	COOKIES-2026-01	t	2026-07-16 01:32:21.873264+00	198.51.100.24	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	\N	\N
3	2	COOKIES_MARKETING	COOKIES-2026-01	f	2026-07-16 01:32:21.873264+00	198.51.100.24	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	\N	\N
\.


--
-- Data for Name: conversation; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.conversation (id, reference, membre_id, intervention_id, sujet, cloturee, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
1	c2424b2f-69b2-43ae-b3e6-c5e63ea52bf5	2	5	Question sur le remplacement des plaquettes	f	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
\.


--
-- Data for Name: demande_annulation; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.demande_annulation (id, reference, commande_id, statut, motif_membre, motif_decision, decide_par, decide_le, avoir_id, date_demande, version, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
\.


--
-- Data for Name: facture; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.facture (id, reference, numero, exercice, sequence_annuelle, commande_id, intervention_id, membre_id, montant_htva, montant_tva, montant_tvac, taux_tva_applique, date_emission, date_echeance, chemin_pdf, created_at, updated_at, created_by, updated_by) FROM stdin;
1	ff550255-aad2-4829-812c-e8bc42e1d243	2026-0001	2026	1	1	\N	2	190.40	39.98	230.38	21.00	2026-08-13 01:37:21.873264+00	\N	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	zone identites	SQL	V1__zone_identites.sql	-900081128	autoservplus	2026-08-25 03:32:19.724965	127	t
2	2	zone catalogue	SQL	V2__zone_catalogue.sql	-1919532117	autoservplus	2026-08-25 03:32:19.911793	93	t
3	3	zone reservations	SQL	V3__zone_reservations.sql	-1656495891	autoservplus	2026-08-25 03:32:20.039453	117	t
4	4	zone vente	SQL	V4__zone_vente.sql	-1050591934	autoservplus	2026-08-25 03:32:20.181623	113	t
5	5	zone atelier	SQL	V5__zone_atelier.sql	-202789729	autoservplus	2026-08-25 03:32:20.322155	71	t
6	6	zone facturation	SQL	V6__zone_facturation.sql	1830838960	autoservplus	2026-08-25 03:32:20.412908	83	t
7	7	zone communication	SQL	V7__zone_communication.sql	-72467850	autoservplus	2026-08-25 03:32:20.517193	85	t
8	8	zone annexes	SQL	V8__zone_annexes.sql	1340591386	autoservplus	2026-08-25 03:32:20.642677	50	t
9	9	sequences numerotation	SQL	V9__sequences_numerotation.sql	-70078398	autoservplus	2026-08-25 03:32:20.712595	15	t
10	10	donnees reference	SQL	V10__donnees_reference.sql	1342999770	autoservplus	2026-08-25 03:32:20.745353	13	t
11	11	rdv creneau unicite partielle	SQL	V11__rdv_creneau_unicite_partielle.sql	-954179322	autoservplus	2026-08-25 03:32:20.775792	13	t
12	12	sequence numero rdv	SQL	V12__sequence_numero_rdv.sql	-1455131357	autoservplus	2026-08-25 03:32:20.804819	6	t
13	13	planification par postes	SQL	V13__planification_par_postes.sql	590353235	autoservplus	2026-08-25 03:32:20.839542	182	t
14	14	exclusion plage ouverture	SQL	V14__exclusion_plage_ouverture.sql	1250082393	autoservplus	2026-08-25 03:32:21.044463	9	t
15	15	correction hash admin	SQL	V15__correction_hash_admin.sql	544598008	autoservplus	2026-08-25 03:32:21.086823	8	t
16	16	donnees demo catalogue	SQL	V16__donnees_demo_catalogue.sql	1439165137	autoservplus	2026-08-25 03:32:21.110486	8	t
17	17	donnees demo postes	SQL	V17__donnees_demo_postes.sql	1655714077	autoservplus	2026-08-25 03:32:21.134218	7	t
18	18	adaptation intervention	SQL	V18__adaptation_intervention.sql	780991549	autoservplus	2026-08-25 03:32:21.157829	10	t
19	19	realignement statut intervention	SQL	V19__realignement_statut_intervention.sql	174167193	autoservplus	2026-08-25 03:32:21.185478	18	t
20	20	marquage lignes intervention	SQL	V20__marquage_lignes_intervention.sql	94522382	autoservplus	2026-08-25 03:32:21.219382	15	t
21	21	devis initial obligatoire	SQL	V21__devis_initial_obligatoire.sql	566433945	autoservplus	2026-08-25 03:32:21.250567	10	t
22	22	alignement accord membre	SQL	V22__alignement_accord_membre.sql	464799101	autoservplus	2026-08-25 03:32:21.275247	13	t
23	23	historique statut intervention	SQL	V23__historique_statut_intervention.sql	1069812671	autoservplus	2026-08-25 03:32:21.305292	25	t
24	24	annulation commande et verrou paiement	SQL	V24__annulation_commande_et_verrou_paiement.sql	1758010526	autoservplus	2026-08-25 03:32:21.345667	16	t
25	25	historique modification catalogue	SQL	V25__historique_modification_catalogue.sql	-1189865987	autoservplus	2026-08-25 03:32:21.377544	50	t
26	26	facturation numerotation et unicite	SQL	V26__facturation_numerotation_et_unicite.sql	75669582	autoservplus	2026-08-25 03:32:21.443092	26	t
27	27	retractation et avoir	SQL	V27__retractation_et_avoir.sql	-1563972579	autoservplus	2026-08-25 03:32:21.486693	71	t
28	28	anonymisation compte	SQL	V28__anonymisation_compte.sql	-202806777	autoservplus	2026-08-25 03:32:21.578501	13	t
29	29	consentement cookies par finalite	SQL	V29__consentement_cookies_par_finalite.sql	1008446080	autoservplus	2026-08-25 03:32:21.62058	8	t
30	30	photo intervention	SQL	V30__photo_intervention.sql	1122899032	autoservplus	2026-08-25 03:32:21.645067	19	t
31	31	renonciation retractation	SQL	V31__renonciation_retractation.sql	-1296810557	autoservplus	2026-08-25 03:32:21.680299	13	t
32	32	intervention depuis commande	SQL	V32__intervention_depuis_commande.sql	-2063279875	autoservplus	2026-08-25 03:32:21.709127	19	t
33	33	versionnage documents	SQL	V33__versionnage_documents.sql	1568283809	autoservplus	2026-08-25 03:32:21.74635	45	t
34	35	cgv 2026 02 conservation dix ans	SQL	V35__cgv_2026_02_conservation_dix_ans.sql	1090591533	autoservplus	2026-08-25 03:32:21.821097	13	t
35	900	donnees demo transactionnelles	SQL	V900__donnees_demo_transactionnelles.sql	-1349215793	autoservplus	2026-08-25 03:32:21.854871	55	t
\.


--
-- Data for Name: historique_modification_catalogue; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.historique_modification_catalogue (id, type_entite, entite_id, champ_modifie, valeur_avant, valeur_apres, horodatage, auteur_id, created_at, updated_at, created_by, updated_by) FROM stdin;
\.


--
-- Data for Name: historique_statut_intervention; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.historique_statut_intervention (id, intervention_id, statut_avant, statut_apres, horodatage, auteur_id, motif, created_at, updated_at, created_by, updated_by) FROM stdin;
1	1	\N	PLANIFIEE	2026-08-03 07:00:00+00	1	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
2	1	PLANIFIEE	EN_COURS	2026-08-03 07:05:00+00	1	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
3	1	EN_COURS	TERMINEE	2026-08-03 08:40:00+00	1	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
4	3	\N	PLANIFIEE	2026-08-24 22:32:21.873264+00	1	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
5	3	PLANIFIEE	EN_COURS	2026-08-24 23:32:21.873264+00	1	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
6	4	\N	PLANIFIEE	2026-08-22 00:32:21.873264+00	1	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
7	4	PLANIFIEE	EN_COURS	2026-08-22 01:32:21.873264+00	1	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
8	4	EN_COURS	SUSPENDUE	2026-08-22 05:32:21.873264+00	1	Batterie non disponible en stock.	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
9	5	\N	PLANIFIEE	2026-08-24 19:32:21.873264+00	1	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
10	5	PLANIFIEE	EN_COURS	2026-08-24 20:32:21.873264+00	1	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
11	5	EN_COURS	ATTENTE_VALIDATION_MEMBRE	2026-08-24 21:32:21.873264+00	1	Depassement du devis : plaquettes a remplacer.	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
12	6	\N	PLANIFIEE	2026-08-15 01:32:21.873264+00	1	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
13	6	PLANIFIEE	ANNULEE	2026-08-16 01:32:21.873264+00	1	Diagnostic realise ailleurs.	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
\.


--
-- Data for Name: indisponibilite; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.indisponibilite (id, reference, poste_id, debut, fin, motif, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
\.


--
-- Data for Name: intervention; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.intervention (id, reference, numero, rdv_id, vehicule_id, statut, diagnostic, montant_devis_htva, montant_reel_htva, depassement_notifie, accord_client, date_accord_client, debut_reel, fin_reelle, kilometrage_releve, version, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by, commentaire_admin, commande_id) FROM stdin;
1	a5591cdd-ba43-4c98-b7bb-144008a2b1cf	INT-2026-0001	3	1	TERMINEE	\N	180.00	\N	f	\N	\N	2026-08-03 07:05:00+00	2026-08-03 08:40:00+00	95800	0	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	\N	\N
2	957e627a-dcab-4dfd-a097-5f62e16d47cf	INT-2026-0002	\N	2	PLANIFIEE	\N	65.00	\N	f	\N	\N	\N	\N	\N	0	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	\N	\N
3	dffd3b23-e75a-4f3d-9c67-4e4f711aad79	INT-2026-0003	\N	1	EN_COURS	\N	120.00	\N	f	\N	\N	2026-08-24 23:32:21.873264+00	\N	96500	0	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	\N	\N
4	da25af50-2e3c-48f6-8fd6-d3cc3d396617	INT-2026-0004	\N	2	SUSPENDUE	\N	118.00	\N	f	\N	\N	2026-08-22 01:32:21.873264+00	\N	31200	0	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	Batterie non disponible en stock, reapprovisionnement attendu.	\N
5	ef3005d8-875e-42ae-b769-43702dd0ce91	INT-2026-0005	\N	1	ATTENTE_VALIDATION_MEMBRE	\N	120.00	\N	f	\N	\N	2026-08-24 20:32:21.873264+00	\N	96520	0	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	\N	\N
6	91fffe8d-df35-417a-8f0b-e994d0ffc320	INT-2026-0006	\N	2	ANNULEE	\N	55.00	\N	f	\N	\N	\N	\N	\N	0	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	Le client a fait realiser le diagnostic ailleurs.	\N
\.


--
-- Data for Name: ligne_intervention; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ligne_intervention (id, intervention_id, service_id, piece_id, libelle_fige, quantite, prix_unitaire_htva, taux_tva, created_at, updated_at, created_by, updated_by, ajoutee_en_cours, accord_membre) FROM stdin;
1	1	2	\N	Revision annuelle	1	180.00	21.00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	f	\N
2	1	\N	2	Filtre a huile	1	12.40	21.00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	f	\N
3	2	1	\N	Vidange standard	1	65.00	21.00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	f	\N
4	3	3	\N	Remplacement plaquettes avant	1	120.00	21.00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	f	\N
5	4	\N	5	Batterie 72 Ah	1	118.00	21.00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	f	\N
6	5	3	\N	Remplacement plaquettes avant	1	120.00	21.00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	f	\N
7	5	\N	3	Jeu de plaquettes avant	1	46.50	21.00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	t	\N
8	6	7	\N	Diagnostic electronique	1	55.00	21.00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	f	\N
\.


--
-- Data for Name: ligne_panier; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ligne_panier (id, panier_id, commande_id, service_id, piece_id, libelle_fige, quantite, prix_unitaire_htva, taux_tva, created_at, updated_at, created_by, updated_by) FROM stdin;
1	\N	1	\N	2	Filtre a huile	1	12.40	21.00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
2	\N	1	\N	4	Pneu 205/55 R16	2	89.00	21.00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo
\.


--
-- Data for Name: message; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.message (id, conversation_id, expediteur_id, role_expediteur, corps, lu, date_envoi, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
1	1	2	MEMBRE	Bonjour, les plaquettes sont-elles vraiment a remplacer maintenant ?	t	2026-08-24 22:32:21.873264+00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
2	1	1	ADMINISTRATEUR	Bonjour, elles sont sous la cote d usure minimale. Le devis est en attente de votre accord.	f	2026-08-24 23:02:21.873264+00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
\.


--
-- Data for Name: notification; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notification (id, membre_id, type, titre, corps, statut, canal, date_envoi, date_lecture, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
1	2	RDV_CONFIRME	Rendez-vous confirme	RDV-2026-0001	NON_LUE	APPLICATION	2026-08-24 01:32:21.873264+00	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
2	2	MESSAGE_RECU	Nouveau message du garage	INT-2026-0005	NON_LUE	APPLICATION	2026-08-24 23:02:21.873264+00	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
3	2	INTERVENTION_TERMINEE	Intervention terminee	INT-2026-0001	LUE	APPLICATION	2026-08-03 08:45:00+00	2026-08-03 22:00:00+00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
4	2	COMMANDE_PAYEE	Commande payee	CMD-2026-0001	LUE	APPLICATION	2026-08-13 01:32:21.873264+00	2026-08-14 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
\.


--
-- Data for Name: paiement; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.paiement (id, reference, commande_id, reference_mollie, cle_idempotence, montant, devise, methode, statut, date_initiation, date_finalisation, created_at, updated_at, created_by, updated_by, version, reference_remboursement) FROM stdin;
1	583fc76c-2373-483b-ae26-753950222cd4	1	demo_tr_0000000001	demo-idem-0000000001	230.38	EUR	\N	REUSSI	2026-08-13 01:32:21.873264+00	2026-08-13 01:36:21.873264+00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	0	\N
\.


--
-- Data for Name: panier; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.panier (id, reference, membre_id, date_expiration, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
1	cbff0717-8742-4e30-9f6c-21e31b049a21	2	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
\.


--
-- Data for Name: parametre_atelier; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.parametre_atelier (id, fuseau_horaire, pas_minutes, tampon_minutes, delai_minimal_heures, horizon_jours, delai_annulation_heures, confirmation_automatique, max_rdv_en_attente_par_membre, updated_at, updated_by) FROM stdin;
1	Europe/Brussels	30	10	24	60	24	f	3	2026-08-25 01:32:20.85+00	\N
\.


--
-- Data for Name: photo; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.photo (id, service_id, piece_id, chemin, texte_alt, ordre, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by, intervention_id) FROM stdin;
\.


--
-- Data for Name: piece; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.piece (id, reference, categorie_id, reference_fabricant, libelle, description, marque, prix_htva, taux_tva, quantite_stock, seuil_alerte, actif, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
1	48495e97-f716-4ec7-af69-5bf70cfb9a8c	7	FA-2201	Filtre a air	Filtre a air panneau, remplacement a chaque revision.	Bosch	18.90	21.00	25	8	t	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
3	ce846e2d-1df3-4e60-98db-93b77afecb30	8	PL-3310	Jeu de plaquettes avant	Jeu de quatre plaquettes, montage avant.	Ferodo	46.50	21.00	12	4	t	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
5	fb40a623-4e73-4ca7-b8f8-5d78cc9dc000	10	BT-7201	Batterie 72 Ah	Batterie 12 V, 72 Ah, 680 A au demarrage.	Varta	118.00	21.00	3	5	t	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
6	29dc8261-d1a3-4a7a-8362-e6971b6da673	11	HU-5W30-5L	Huile moteur 5W30 (5 L)	Huile synthetique 5W30, bidon de cinq litres.	Total	42.00	21.00	30	10	t	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
2	e3994b1a-9de0-4477-af46-7e0c8df8a58f	7	FH-1042	Filtre a huile	Filtre a huile vissable, moteurs essence et diesel courants.	Mann	12.40	21.00	39	10	t	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
4	65405e23-6247-4016-a315-a01db8988c5e	9	PN-2055516	Pneu 205/55 R16	Pneu tourisme quatre saisons, indice de charge 91V.	Michelin	89.00	21.00	14	8	t	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
\.


--
-- Data for Name: place_parking; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.place_parking (id, numero, type, tarif_jour_htva, actif, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
1	P01	STANDARD	8.00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
2	P02	STANDARD	8.00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
3	P03	STANDARD	8.00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
4	P04	STANDARD	8.00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
5	P05	COUVERTE	12.00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
6	P06	COUVERTE	12.00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
7	P07	GRANDE	14.00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
8	P08	PMR	8.00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
\.


--
-- Data for Name: plage_ouverture; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.plage_ouverture (id, jour_semaine, heure_debut, heure_fin, actif, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
1	1	08:00:00	12:00:00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
2	1	13:00:00	18:00:00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
3	2	08:00:00	12:00:00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
4	2	13:00:00	18:00:00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
5	3	08:00:00	12:00:00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
6	3	13:00:00	18:00:00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
7	4	08:00:00	12:00:00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
8	4	13:00:00	18:00:00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
9	5	08:00:00	12:00:00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
10	5	13:00:00	17:00:00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
11	6	09:00:00	13:00:00	t	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	\N	\N	\N	\N
\.


--
-- Data for Name: poste_atelier; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.poste_atelier (id, reference, libelle, description, ordre, actif, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
1	f94eec32-bc07-4939-8bcc-6d097bdee285	Pont 1	Pont elevateur principal	1	t	2026-08-25 01:32:21.140664+00	2026-08-25 01:32:21.140664+00	\N	\N	\N	\N
2	ec12f90e-b83c-4130-8f2c-aca789b51e5e	Pont 2	Pont elevateur secondaire	2	t	2026-08-25 01:32:21.140664+00	2026-08-25 01:32:21.140664+00	\N	\N	\N	\N
3	aefd9634-79bf-4c1b-a736-c4eeca90bf9d	Baie diagnostic	Baie sans elevation pour diagnostic et pneumatique	3	t	2026-08-25 01:32:21.140664+00	2026-08-25 01:32:21.140664+00	\N	\N	\N	\N
\.


--
-- Data for Name: rdv; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.rdv (id, reference, numero, membre_id, vehicule_id, statut, commentaire, motif_refus, date_annulation, version, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by, debut, fin, poste_id) FROM stdin;
1	5d9e1dd3-21cc-490d-8dec-07d0cad7e359	RDV-2026-0001	2	1	CONFIRME	Bruit de freinage a l avant depuis une semaine.	\N	\N	0	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	2026-09-07 07:00:00+00	2026-09-07 08:00:00+00	1
2	ac8d18e8-6ea0-4b1f-a804-a5eb54025e90	RDV-2026-0002	2	2	EN_ATTENTE	Vidange annuelle.	\N	\N	0	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	2026-09-07 08:30:00+00	2026-09-07 09:00:00+00	1
3	cd052dfe-dc4c-4971-a6d7-3f7ee9ad7fe8	RDV-2026-0003	2	1	HONORE	Revision annuelle.	\N	\N	0	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	2026-08-03 07:00:00+00	2026-08-03 08:30:00+00	1
4	54e12550-c3ff-4119-9877-70acedf5d0af	RDV-2026-0004	2	2	ANNULE	Permutation des pneus.	\N	2026-08-02 01:32:21.873264+00	0	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	2026-08-17 12:00:00+00	2026-08-17 12:30:00+00	2
5	4f6a4b1f-3faa-4fe6-9621-eeb9f7a4c6b2	RDV-2026-0005	2	1	REFUSE	Diagnostic electronique.	Atelier ferme ce jour pour formation du personnel.	\N	0	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	2026-08-24 13:00:00+00	2026-08-24 13:30:00+00	2
6	e9604347-7881-406b-b71b-dcd8eaf1d95b	RDV-2026-0006	2	2	ABSENT	Montage d un pneu.	\N	\N	0	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	2026-08-31 09:00:00+00	2026-08-31 09:30:00+00	2
\.


--
-- Data for Name: rdv_service; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.rdv_service (id, rdv_id, service_id, quantite, prix_unitaire_htva, taux_tva, created_at, updated_at, created_by, updated_by) FROM stdin;
1	1	3	1	120.00	21.00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	\N	\N
2	3	2	1	180.00	21.00	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	\N	\N
\.


--
-- Data for Name: reservation_parking; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.reservation_parking (id, reference, numero, membre_id, vehicule_id, place_id, paiement_id, date_debut, date_fin, montant_htva, montant_tvac, statut, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
\.


--
-- Data for Name: service; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.service (id, reference, categorie_id, code, libelle, description, prix_htva, taux_tva, duree_minutes, actif, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
1	cafbab50-bd8c-4fd3-93a3-e22eaad0bc58	1	VIDANGE_STD	Vidange standard	\N	65.00	21.00	30	t	2026-08-25 01:32:21.115917+00	2026-08-25 01:32:21.115917+00	\N	\N	\N	\N
2	76a8757b-2594-4311-8f59-d58b521ba221	1	REVISION_ANNUELLE	Revision annuelle	\N	180.00	21.00	90	t	2026-08-25 01:32:21.115917+00	2026-08-25 01:32:21.115917+00	\N	\N	\N	\N
3	e81438aa-1dbc-4a5a-aa6a-2f2ec72ec8d5	2	PLAQUETTES_AV	Remplacement plaquettes avant	\N	120.00	21.00	60	t	2026-08-25 01:32:21.115917+00	2026-08-25 01:32:21.115917+00	\N	\N	\N	\N
4	b1c9bc09-27c7-4c2e-9730-56614c69edf7	2	PLAQUETTES_AR	Remplacement plaquettes arriere	\N	100.00	21.00	60	t	2026-08-25 01:32:21.115917+00	2026-08-25 01:32:21.115917+00	\N	\N	\N	\N
5	20c00774-93b9-4685-9c77-c84dd881fb36	3	MONTAGE_PNEU	Montage et equilibrage (1 pneu)	\N	25.00	21.00	30	t	2026-08-25 01:32:21.115917+00	2026-08-25 01:32:21.115917+00	\N	\N	\N	\N
6	350c4a26-ef41-4027-9657-419d0862ec59	3	PERMUTATION_PNEUS	Permutation des 4 pneus	\N	40.00	21.00	30	t	2026-08-25 01:32:21.115917+00	2026-08-25 01:32:21.115917+00	\N	\N	\N	\N
7	426d976d-2d54-4658-8ad1-c06078bdd78d	4	DIAG_ELEC	Diagnostic electronique	\N	55.00	21.00	30	t	2026-08-25 01:32:21.115917+00	2026-08-25 01:32:21.115917+00	\N	\N	\N	\N
8	7af41513-d75d-4a25-9228-3495485db2a8	5	RETOUCHE_PEINTURE	Retouche peinture	\N	90.00	21.00	60	t	2026-08-25 01:32:21.115917+00	2026-08-25 01:32:21.115917+00	\N	\N	\N	\N
9	859211a0-40c0-4d54-ad47-b132f543d7bb	6	PREPARATION_CT	Preparation controle technique	\N	45.00	21.00	30	t	2026-08-25 01:32:21.115917+00	2026-08-25 01:32:21.115917+00	\N	\N	\N	\N
\.


--
-- Data for Name: utilisateur; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.utilisateur (id, reference, type_utilisateur, email, mot_de_passe_hache, nom, prenom, telephone, rue, numero_rue, code_postal, localite, pays, langue, statut, email_verifie, jeton_verification, jeton_expiration, derniere_connexion, tentatives_echouees, verrouille_jusqu_a, fonction, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by, anonymise_le) FROM stdin;
1	efac7a93-b553-49ba-8949-8c02619cab55	ADMINISTRATEUR	admin@autoservplus.be	$2a$12$gnSV2iew72ylfGo24d4qB.gq7DPefhBG9efPcE2EBl8QfjAbQgoYG	Administrateur	Systeme	\N	\N	\N	\N	\N	Belgique	fr	ACTIF	t	\N	\N	\N	0	\N	Gerant	2026-08-25 01:32:20.752766+00	2026-08-25 01:32:20.752766+00	migration	migration	\N	\N	\N
2	835fa6c6-ac2f-4b66-a46a-5f335d788ba3	MEMBRE	marie.dupont@demo.test	$2a$12$o7vMe8EtQXWUxsZzGpLTQ.5xkKUW71RmSkEioCAOWoGBgG6kQ5NdS	Dupont	Marie	+32 470 00 00 01	Rue des Ateliers	18	1000	Bruxelles	Belgique	fr	ACTIF	t	\N	\N	\N	0	\N	\N	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N	\N
\.


--
-- Data for Name: vehicule; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.vehicule (id, reference, membre_id, plaque, marque, modele, motorisation, annee, kilometrage, numero_chassis, actif, created_at, updated_at, created_by, updated_by, deleted_at, deleted_by) FROM stdin;
1	8ad9a90c-8af7-4212-b9c6-a5060a2e3359	2	1-DEM-001	Volkswagen	Golf	DIESEL	2019	96500	WVWZZZ1KZAW000001	t	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
2	7610d519-fc54-49c9-becb-86570871e7b4	2	1-DEM-002	Renault	Clio	ESSENCE	2022	31200	VF1RJA00000000002	t	2026-08-25 01:32:21.873264+00	2026-08-25 01:32:21.873264+00	demo	demo	\N	\N
\.


--
-- Data for Name: version_document; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.version_document (id, type_document, version, langue, date_effet, contenu, empreinte, actif, created_at, updated_at, created_by, updated_by) FROM stdin;
2	COOKIES	COOKIES-2026-01	fr	2026-08-23 22:00:00+00	Votre choix sur les cookies\nCe site dépose des cookies strictement nécessaires à son fonctionnement. Les autres finalités ne sont activées qu'avec votre accord. Vous pouvez continuer à naviguer sans répondre.\nStrictement nécessaires\nToujours actifs\nMaintenir votre session et votre panier, et protéger les formulaires contre la falsification de requête.\nDurée : la session, et six mois pour la mémorisation de ce choix.\nMesure d'audience\nAutoriser la mesure d'audience\nComprendre quelles pages sont consultées, afin d'améliorer le site.\nDurée : treize mois au maximum.\nMarketing\nAutoriser la publicité ciblée\nVous proposer des offres du garage adaptées à ce que vous avez consulté.\nDurée : treize mois au maximum.\nÀ ce jour, AutoServ+ n'installe aucun cookie de mesure d'audience ni de marketing. Votre choix est enregistré et conditionnera leur chargement s'ils sont ajoutés.\nVotre choix est conservé six mois, puis la question vous est reposée.\nVous pouvez modifier votre choix à tout moment ; il s'applique immédiatement. Les cookies strictement nécessaires ne peuvent pas être désactivés : sans eux, le site ne fonctionne plus.	bf9ba233cb33e346f06cc59f0eb2a169ad2c9ca0fce85fcb0ba82185e8348250	t	2026-08-25 01:32:21.766689+00	2026-08-25 01:32:21.766689+00	systeme	systeme
3	RENONCIATION_RETRACTATION	VI53-2026-01	fr	2026-08-23 22:00:00+00	Je demande que la prestation soit exécutée immédiatement et je reconnais perdre mon droit de rétractation une fois qu'elle aura été pleinement exécutée.\nCase facultative. Si vous ne la cochez pas, vous conservez 14 jours pour vous rétracter ; la prestation ne sera alors pas exécutée avant ce délai, sauf accord contraire avec le garage.	85a677cadae8fa4198e9336fb308e088bff97310af6a28670a504a983839e09e	t	2026-08-25 01:32:21.766689+00	2026-08-25 01:32:21.766689+00	systeme	systeme
5	COOKIES	COOKIES-2026-01	nl	2026-08-23 22:00:00+00	Uw keuze over cookies\nDeze site plaatst cookies die strikt noodzakelijk zijn voor de werking ervan. De andere doeleinden worden pas geactiveerd met uw toestemming. U kunt verder surfen zonder te antwoorden.\nStrikt noodzakelijk\nAltijd actief\nUw sessie en uw winkelmandje behouden, en de formulieren beschermen tegen vervalsing van aanvragen.\nDuur: de sessie, en zes maanden voor het bewaren van deze keuze.\nPublieksmeting\nPublieksmeting toestaan\nBegrijpen welke pagina's worden geraadpleegd, om de site te verbeteren.\nDuur: hoogstens dertien maanden.\nMarketing\nGerichte reclame toestaan\nU aanbiedingen van de garage voorstellen die aansluiten bij wat u hebt geraadpleegd.\nDuur: hoogstens dertien maanden.\nOp dit ogenblik plaatst AutoServ+ geen enkele cookie voor publieksmeting of marketing. Uw keuze wordt bewaard en bepaalt of ze geladen worden zodra ze worden toegevoegd.\nUw keuze wordt zes maanden bewaard, daarna wordt de vraag opnieuw gesteld.\nU kunt uw keuze op elk moment wijzigen; ze geldt onmiddellijk. Strikt noodzakelijke cookies kunnen niet worden uitgeschakeld: zonder hen werkt de site niet meer.	8d30d7dae769d62d7be3449ea54410b0c8f3a3cdd79ce2e3681bf2c94800a9e4	t	2026-08-25 01:32:21.766689+00	2026-08-25 01:32:21.766689+00	systeme	systeme
6	RENONCIATION_RETRACTATION	VI53-2026-01	nl	2026-08-23 22:00:00+00	Ik vraag de onmiddellijke uitvoering van de dienst en erken dat ik mijn herroepingsrecht verlies zodra deze volledig is uitgevoerd.\nOptioneel. Vinkt u dit niet aan, dan behoudt u 14 dagen om te herroepen; de dienst wordt dan niet vóór die termijn uitgevoerd, tenzij anders overeengekomen met de garage.	2601dce86f0dc13f6116af44ce49dfb30732c4d43978d6a210c832566f20d4db	t	2026-08-25 01:32:21.766689+00	2026-08-25 01:32:21.766689+00	systeme	systeme
8	COOKIES	COOKIES-2026-01	en	2026-08-23 22:00:00+00	Your cookie choice\nThis site sets cookies that are strictly necessary for it to work. Other purposes are only enabled with your agreement. You can keep browsing without answering.\nStrictly necessary\nAlways active\nKeeping your session and your basket, and protecting forms against request forgery.\nDuration: the session, and six months for storing this choice.\nAudience measurement\nAllow audience measurement\nUnderstanding which pages are viewed, in order to improve the site.\nDuration: thirteen months at most.\nMarketing\nAllow targeted advertising\nOffering you garage deals matching what you have viewed.\nDuration: thirteen months at most.\nAs of today, AutoServ+ sets no audience measurement or marketing cookie. Your choice is recorded and will govern their loading if they are added.\nYour choice is kept for six months, after which you will be asked again.\nYou can change your choice at any time; it takes effect immediately. Strictly necessary cookies cannot be disabled: without them the site no longer works.	7b17cc85e719fd0f6f3b3a945ed974b00367dfb8706bab0be903746007fb17bf	t	2026-08-25 01:32:21.766689+00	2026-08-25 01:32:21.766689+00	systeme	systeme
9	RENONCIATION_RETRACTATION	VI53-2026-01	en	2026-08-23 22:00:00+00	I request immediate performance of the service and acknowledge that I lose my right of withdrawal once it has been fully performed.\nOptional. If you leave it unticked, you keep 14 days to withdraw; the service will then not be performed before that period, unless otherwise agreed with the garage.	98bf89cbbc6e3814e644d10f7dce189df303e171c8d4fc2ffbbf6da694516c2b	t	2026-08-25 01:32:21.766689+00	2026-08-25 01:32:21.766689+00	systeme	systeme
10	CGV	CGV-2026-02	fr	2026-08-24 22:00:00+00	Conditions générales de vente\nLes présentes conditions régissent la vente de pièces et de prestations d'atelier par l'intermédiaire de la plateforme AutoServ+.\nArticle 1 — Vendeur\nLe vendeur est le garage identifié ci-dessous, inscrit à la Banque-Carrefour des Entreprises et assujetti à la taxe sur la valeur ajoutée.\nArticle 2 — Champ d'application et acceptation\nToute commande passée sur la plateforme suppose l'acceptation préalable et expresse des présentes conditions. Cette acceptation est recueillie par une case à cocher au récapitulatif de commande ; elle est horodatée et conservée avec la version du document acceptée et l'adresse IP utilisée.\nArticle 3 — Prix et taxe sur la valeur ajoutée\nLes prix sont indiqués en euros, hors taxe et taxe comprise. Les taux appliqués sont les taux belges de 0, 6, 12 et 21 %. Le prix, le libellé et le taux de taxe d'un article sont figés au moment où il est ajouté au panier : une modification ultérieure du catalogue reste sans effet sur un panier déjà constitué.\nArticle 4 — Commande\nLa commande se forme en trois temps : constitution du panier, récapitulatif reprenant le détail des lignes et des montants, puis validation. Le bouton de validation indique expressément que la commande oblige au paiement. Un panier ne peut contenir que des pièces ou que des prestations, jamais les deux à la fois.\nArticle 5 — Paiement\nLe paiement s'effectue en ligne auprès d'un prestataire de services de paiement. Aucune donnée de carte n'est collectée ni conservée par le garage. La commande n'est réputée payée qu'après confirmation du statut par le prestataire.\nArticle 6 — Retrait et exécution\nLes pièces commandées sont retirées au garage ; aucune expédition n'est proposée. L'achat en ligne d'une prestation ne réserve pas de créneau : le garage ouvre le dossier d'atelier et convient de la date avec le client.\nLes biens commandés (pièces, accessoires) sont retirés sur place, à l'adresse du garage indiquée à l'article 1 ; aucune expédition n'est proposée. Les prestations de service sont exécutées dans l'atelier du garage, à la date convenue lors de la réservation ou, à défaut, dans un délai raisonnable communiqué au client. La date d'exécution prévue est rappelée dans le récapitulatif de commande et dans l'e-mail de confirmation, qui constitue le support durable de l'information précontractuelle (article VI.45 §7 du Code de droit économique).\nArticle 7 — Droit de rétractation\nLe consommateur dispose d'un délai de 14 jours pour se rétracter, sans avoir à motiver sa décision. Ce délai court à compter de la conclusion de la commande. La demande se dépose depuis l'espace membre ; le garage l'examine et notifie sa décision. En cas d'acceptation, une note de crédit est émise et le remboursement intervient par le même moyen de paiement que celui employé lors de l'achat.\nLe consommateur peut exercer son droit de rétractation sans avoir à motiver sa décision et sans pénalité (article VI.47 du Code de droit économique). Pour l'exercer, il notifie sa décision au garage au moyen d'une déclaration dénuée d'ambiguïté (courrier postal ou courrier électronique aux coordonnées de l'article 1), ou en utilisant le formulaire type de rétractation mis à sa disposition, téléchargeable et accessible par un lien permanent dans son espace membre. Le garage rembourse la totalité des sommes versées dans les quatorze jours suivant la réception de la rétractation, en utilisant le même moyen de paiement que celui employé lors de la commande, sauf accord exprès du consommateur pour un autre moyen. Ce droit ne s'applique pas dans les cas d'exclusion prévus à l'article VI.53 du Code de droit économique, notamment celui décrit à l'article 8 des présentes (prestations pleinement exécutées avec accord préalable exprès).\nArticle 8 — Prestations pleinement exécutées\nLorsque le client demande l'exécution d'une prestation avant la fin du délai de rétractation, il lui est proposé de reconnaître expressément qu'il perdra ce droit une fois la prestation pleinement exécutée. Cette renonciation est facultative, n'est jamais pré-cochée, et sa preuve est conservée. Tant que la prestation n'est pas pleinement exécutée, le droit de rétractation subsiste.\nArticle 9 — Facture\nUne facture est émise pour toute commande payée et mise à disposition au format PDF dans l'espace membre. Les factures sont numérotées de manière continue et sans interruption, par exercice. Elles sont conservées dix ans, conformément à la législation relative à la taxe sur la valeur ajoutée.\nArticle 10 — Garantie légale de conformité\nPour les biens vendus au consommateur, la garantie légale de conformité de deux ans est applicable (articles 1649bis et suivants de l'ancien Code civil, désormais intégrés au livre 5 du nouveau Code civil). Cette garantie est due par le vendeur, c'est-à-dire le garage. Pour les prestations de service (entretien, réparation, diagnostic, etc.), le garage est tenu d'une obligation de conformité de droit commun : la prestation doit être conforme à ce qui a été convenu ; cette obligation n'est pas assortie d'une durée fixe mais reste encadrée par les règles de la prescription. La présente garantie légale s'applique indépendamment de toute garantie commerciale éventuelle.\nArticle 11 — Responsabilité\nLe garage est responsable de la bonne exécution des prestations vendues, dans les conditions du droit commun (livre 6 du Code civil, entré en vigueur le 1er janvier 2025) et des présentes conditions. Les informations publiées sur le site sont fournies à titre indicatif ; le garage s'efforce de les tenir exactes et à jour sans garantir leur exhaustivité ou l'absence d'erreur. La responsabilité du garage ne peut en aucun cas être exclue ou limitée en cas de faute lourde, de dol, d'atteinte à la vie ou à l'intégrité physique, ni pour les obligations légales impératives — notamment la garantie légale de conformité et la sécurité des produits et services. Le garage n'assume aucune responsabilité quant au contenu des sites tiers accessibles par des liens, sur lesquels il n'exerce aucun contrôle.\nArticle 12 — Données personnelles\nLe traitement des données personnelles est décrit dans la politique de confidentialité, accessible depuis chaque page du site.\nArticle 13 — Droit applicable et litiges\nLes présentes conditions sont régies par le droit belge. En cas de litige, les parties rechercheront une solution amiable avant toute action judiciaire. Le consommateur est informé de l'existence du Service de médiation pour le consommateur (Boulevard du Roi Albert II 8, 1000 Bruxelles — mediationconsommateur.be) et, pour les achats en ligne, de la plateforme européenne de règlement en ligne des litiges (ec.europa.eu/consumers/odr). À défaut de règlement amiable, les cours et tribunaux belges sont compétents ; le consommateur conserve le bénéfice des règles protectrices lui permettant, le cas échéant, de saisir la juridiction de son lieu de domicile.	5f850ad3f585526cbcf7a00dd96a4be439d0e588c34eb6ca837537f0d6e0163c	t	2026-08-25 01:32:21.831384+00	2026-08-25 01:32:21.831384+00	systeme	systeme
11	CGV	CGV-2026-02	nl	2026-08-24 22:00:00+00	Algemene verkoopvoorwaarden\nDeze voorwaarden zijn van toepassing op de verkoop van onderdelen en werkplaatsdiensten via het platform AutoServ+.\nArtikel 1 — Verkoper\nDe verkoper is de hieronder vermelde garage, ingeschreven bij de Kruispuntbank van Ondernemingen en btw-plichtig.\nArtikel 2 — Toepassingsgebied en aanvaarding\nElke bestelling op het platform veronderstelt de voorafgaande en uitdrukkelijke aanvaarding van deze voorwaarden. Die aanvaarding wordt verzameld via een aankruisvakje op het besteloverzicht; zij wordt met tijdstempel bewaard, samen met de aanvaarde versie van het document en het gebruikte IP-adres.\nArtikel 3 — Prijzen en belasting over de toegevoegde waarde\nDe prijzen worden vermeld in euro, exclusief en inclusief belasting. De toegepaste tarieven zijn de Belgische tarieven van 0, 6, 12 en 21 %. De prijs, de omschrijving en het belastingtarief van een artikel worden vastgelegd op het ogenblik waarop het aan het winkelmandje wordt toegevoegd: een latere wijziging van de catalogus heeft geen invloed op een reeds samengesteld mandje.\nArtikel 4 — Bestelling\nDe bestelling komt in drie stappen tot stand: samenstelling van het mandje, overzicht met de details van de lijnen en de bedragen, en vervolgens bevestiging. De bevestigingsknop vermeldt uitdrukkelijk dat de bestelling een betalingsverplichting inhoudt. Een mandje kan uitsluitend onderdelen of uitsluitend diensten bevatten, nooit beide tegelijk.\nArtikel 5 — Betaling\nDe betaling verloopt online via een betaaldienstaanbieder. Er worden door de garage geen kaartgegevens verzameld of bewaard. De bestelling geldt pas als betaald na bevestiging van de status door de dienstverlener.\nArtikel 6 — Afhaling en uitvoering\nBestelde onderdelen worden in de garage afgehaald; er wordt geen verzending aangeboden. De online aankoop van een dienst reserveert geen tijdslot: de garage opent het werkplaatsdossier en spreekt de datum af met de klant.\nDe bestelde goederen (onderdelen, accessoires) worden ter plaatse afgehaald, op het adres van de garage vermeld in artikel 1; er wordt geen verzending aangeboden. De diensten worden uitgevoerd in de werkplaats van de garage, op de bij de reservatie afgesproken datum of, bij gebreke daarvan, binnen een redelijke termijn die aan de klant wordt meegedeeld. De voorziene uitvoeringsdatum wordt herhaald in het besteloverzicht en in de bevestigingsmail, die de duurzame gegevensdrager van de precontractuele informatie vormt (artikel VI.45 §7 van het Wetboek van economisch recht).\nArtikel 7 — Herroepingsrecht\nDe consument beschikt over een termijn van 14 dagen om zich te herroepen, zonder opgave van reden. Deze termijn loopt vanaf het sluiten van de bestelling. De aanvraag wordt ingediend vanuit de ledenruimte; de garage onderzoekt ze en deelt haar beslissing mee. Bij aanvaarding wordt een creditnota opgesteld en gebeurt de terugbetaling via hetzelfde betaalmiddel als bij de aankoop.\nDe consument kan zijn herroepingsrecht uitoefenen zonder opgave van reden en zonder boete (artikel VI.47 van het Wetboek van economisch recht). Om het uit te oefenen, deelt hij zijn beslissing aan de garage mee door middel van een ondubbelzinnige verklaring (brief of e-mail naar de contactgegevens van artikel 1), of met het modelformulier voor herroeping dat hem ter beschikking wordt gesteld, downloadbaar en toegankelijk via een permanente link in zijn ledenruimte. De garage betaalt alle gestorte bedragen terug binnen veertien dagen na ontvangst van de herroeping, met hetzelfde betaalmiddel als dat van de bestelling, tenzij de consument uitdrukkelijk met een ander middel instemt. Dit recht geldt niet in de uitsluitingsgevallen bepaald in artikel VI.53 van het Wetboek van economisch recht, met name het geval beschreven in artikel 8 van deze voorwaarden (volledig uitgevoerde diensten met voorafgaande uitdrukkelijke instemming).\nArtikel 8 — Volledig uitgevoerde diensten\nWanneer de klant vraagt dat een dienst wordt uitgevoerd vóór het verstrijken van de herroepingstermijn, wordt hem voorgesteld uitdrukkelijk te erkennen dat hij dat recht verliest zodra de dienst volledig is uitgevoerd. Die afstand is facultatief, wordt nooit vooraf aangevinkt, en het bewijs ervan wordt bewaard. Zolang de dienst niet volledig is uitgevoerd, blijft het herroepingsrecht bestaan.\nArtikel 9 — Factuur\nVoor elke betaalde bestelling wordt een factuur opgesteld en in pdf-formaat ter beschikking gesteld in de ledenruimte. De facturen worden per boekjaar doorlopend en zonder onderbreking genummerd. Zij worden tien jaar bewaard, overeenkomstig de btw-wetgeving.\nArtikel 10 — Wettelijke conformiteitsgarantie\nVoor goederen die aan de consument worden verkocht, geldt de wettelijke conformiteitsgarantie van twee jaar (artikelen 1649bis en volgende van het oud Burgerlijk Wetboek, thans opgenomen in boek 5 van het nieuw Burgerlijk Wetboek). Deze garantie is verschuldigd door de verkoper, dat wil zeggen de garage. Voor de diensten (onderhoud, herstelling, diagnose, enz.) rust op de garage een gemeenrechtelijke conformiteitsverplichting: de dienst moet overeenstemmen met wat is overeengekomen; aan die verplichting is geen vaste duur verbonden, maar zij blijft omkaderd door de verjaringsregels. Deze wettelijke garantie geldt ongeacht enige commerciële waarborg.\nArtikel 11 — Aansprakelijkheid\nDe garage is aansprakelijk voor de goede uitvoering van de verkochte prestaties, onder de voorwaarden van het gemeen recht (boek 6 van het Burgerlijk Wetboek, in werking getreden op 1 januari 2025) en van deze voorwaarden. De op de site gepubliceerde informatie wordt ter indicatie verstrekt; de garage streeft ernaar ze juist en actueel te houden zonder de volledigheid ervan of de afwezigheid van fouten te waarborgen. De aansprakelijkheid van de garage kan in geen geval worden uitgesloten of beperkt in geval van zware fout, bedrog, aantasting van het leven of de lichamelijke integriteit, noch voor de dwingende wettelijke verplichtingen — met name de wettelijke conformiteitsgarantie en de veiligheid van producten en diensten. De garage draagt geen enkele aansprakelijkheid voor de inhoud van sites van derden die via links toegankelijk zijn en waarover zij geen enkele controle uitoefent.\nArtikel 12 — Persoonsgegevens\nDe verwerking van persoonsgegevens wordt beschreven in het privacybeleid, dat vanaf elke pagina van de site toegankelijk is.\nArtikel 13 — Toepasselijk recht en geschillen\nDeze voorwaarden worden beheerst door het Belgisch recht. Bij een geschil zoeken de partijen een minnelijke oplossing vóór elke gerechtelijke stap. De consument wordt ingelicht over het bestaan van de Consumentenombudsdienst (Koning Albert II-laan 8, 1000 Brussel — consumentenombudsdienst.be) en, voor onlineaankopen, over het Europese platform voor onlinegeschillenbeslechting (ec.europa.eu/consumers/odr). Bij gebrek aan een minnelijke regeling zijn de Belgische hoven en rechtbanken bevoegd; de consument behoudt het voordeel van de beschermende regels die hem in voorkomend geval toelaten de rechtbank van zijn woonplaats te vatten.	70a5805d6834e303916ca12bb3f4281319b37e067946b124d3892dfb00413313	t	2026-08-25 01:32:21.831384+00	2026-08-25 01:32:21.831384+00	systeme	systeme
12	CGV	CGV-2026-02	en	2026-08-24 22:00:00+00	Terms and conditions of sale\nThese terms govern the sale of parts and workshop services through the AutoServ+ platform.\nArticle 1 — Seller\nThe seller is the garage identified below, registered with the Crossroads Bank for Enterprises and liable for value added tax.\nArticle 2 — Scope and acceptance\nEvery order placed on the platform requires the prior and express acceptance of these terms. That acceptance is collected through a checkbox on the order summary; it is time-stamped and stored together with the version of the document accepted and the IP address used.\nArticle 3 — Prices and value added tax\nPrices are shown in euro, excluding and including tax. The rates applied are the Belgian rates of 0, 6, 12 and 21 %. The price, description and tax rate of an item are frozen at the moment it is added to the basket: a later change to the catalogue has no effect on a basket already assembled.\nArticle 4 — Order\nAn order is formed in three steps: building the basket, a summary showing each line and each amount, then confirmation. The confirmation button states expressly that the order carries an obligation to pay. A basket may contain either parts or services, never both at once.\nArticle 5 — Payment\nPayment is made online through a payment service provider. No card data is collected or stored by the garage. An order is treated as paid only after the provider has confirmed its status.\nArticle 6 — Collection and performance\nOrdered parts are collected at the garage; no shipping is offered. Buying a service online does not book a time slot: the garage opens the workshop file and agrees a date with the customer.\nGoods ordered (parts, accessories) are collected on site, at the garage address given in Article 1; no shipping is offered. Services are performed in the garage workshop, on the date agreed when booking or, failing that, within a reasonable period notified to the customer. The expected performance date is repeated in the order summary and in the confirmation e-mail, which constitutes the durable medium for the pre-contractual information (Article VI.45 §7 of the Code of Economic Law).\nArticle 7 — Right of withdrawal\nThe consumer has 14 days to withdraw, without having to give any reason. That period runs from the conclusion of the order. The request is submitted from the member area; the garage examines it and notifies its decision. If accepted, a credit note is issued and the refund is made using the same means of payment as the purchase.\nThe consumer may exercise the right of withdrawal without having to give any reason and without penalty (Article VI.47 of the Code of Economic Law). To do so, they notify the garage of their decision by an unambiguous statement (letter or e-mail to the contact details in Article 1), or by using the model withdrawal form made available to them, downloadable and reachable through a permanent link in their member area. The garage refunds all sums paid within fourteen days of receiving the withdrawal, using the same means of payment as the one used for the order, unless the consumer expressly agrees to another means. This right does not apply in the cases of exclusion set out in Article VI.53 of the Code of Economic Law, in particular the one described in Article 8 of these terms (services fully performed with prior express agreement).\nArticle 8 — Fully performed services\nWhere the customer asks for a service to be performed before the withdrawal period ends, they are offered the option of expressly acknowledging that they will lose that right once the service has been fully performed. This waiver is optional, is never pre-ticked, and evidence of it is retained. As long as the service has not been fully performed, the right of withdrawal remains.\nArticle 9 — Invoice\nAn invoice is issued for every paid order and made available as a PDF in the member area. Invoices are numbered continuously and without gaps, per financial year. They are kept for ten years, in accordance with value added tax legislation.\nArticle 10 — Legal guarantee of conformity\nFor goods sold to a consumer, the two-year legal guarantee of conformity applies (Articles 1649bis et seq. of the former Civil Code, now incorporated into Book 5 of the new Civil Code). This guarantee is owed by the seller, that is to say the garage. For services (servicing, repair, diagnosis, etc.), the garage is bound by an ordinary-law obligation of conformity: the service must match what was agreed; that obligation carries no fixed duration but remains framed by the rules on limitation periods. This legal guarantee applies irrespective of any commercial warranty.\nArticle 11 — Liability\nThe garage is liable for the proper performance of the services sold, under the conditions of the general law (Book 6 of the Civil Code, in force since 1 January 2025) and of these terms. The information published on the site is provided for guidance; the garage endeavours to keep it accurate and up to date without guaranteeing that it is exhaustive or free of error. The liability of the garage may in no case be excluded or limited in the event of gross negligence, fraud, harm to life or physical integrity, nor for mandatory legal obligations — in particular the legal guarantee of conformity and the safety of products and services. The garage assumes no liability for the content of third-party sites reachable through links, over which it exercises no control.\nArticle 12 — Personal data\nThe processing of personal data is described in the privacy policy, which is reachable from every page of the site.\nArticle 13 — Applicable law and disputes\nThese terms are governed by Belgian law. In the event of a dispute, the parties will seek an amicable solution before any legal action. The consumer is informed of the existence of the Consumer Mediation Service (Boulevard du Roi Albert II 8, 1000 Brussels — mediationconsommateur.be) and, for online purchases, of the European online dispute resolution platform (ec.europa.eu/consumers/odr). Failing an amicable settlement, the Belgian courts have jurisdiction; the consumer retains the benefit of the protective rules allowing them, where applicable, to bring proceedings before the court of their place of domicile.	a91a1c38867fd7ac316d04da60cc3c5474ceec17a00e7d40d3a5088ccbbb064c	t	2026-08-25 01:32:21.831384+00	2026-08-25 01:32:21.831384+00	systeme	systeme
7	CGV	CGV-2026-01	en	2026-08-23 22:00:00+00	Terms and conditions of sale\nThese terms govern the sale of parts and workshop services through the AutoServ+ platform.\nArticle 1 — Seller\nThe seller is the garage identified below, registered with the Crossroads Bank for Enterprises and liable for value added tax.\nArticle 2 — Scope and acceptance\nEvery order placed on the platform requires the prior and express acceptance of these terms. That acceptance is collected through a checkbox on the order summary; it is time-stamped and stored together with the version of the document accepted and the IP address used.\nArticle 3 — Prices and value added tax\nPrices are shown in euro, excluding and including tax. The rates applied are the Belgian rates of 0, 6, 12 and 21 %. The price, description and tax rate of an item are frozen at the moment it is added to the basket: a later change to the catalogue has no effect on a basket already assembled.\nArticle 4 — Order\nAn order is formed in three steps: building the basket, a summary showing each line and each amount, then confirmation. The confirmation button states expressly that the order carries an obligation to pay. A basket may contain either parts or services, never both at once.\nArticle 5 — Payment\nPayment is made online through a payment service provider. No card data is collected or stored by the garage. An order is treated as paid only after the provider has confirmed its status.\nArticle 6 — Collection and performance\nOrdered parts are collected at the garage; no shipping is offered. Buying a service online does not book a time slot: the garage opens the workshop file and agrees a date with the customer.\nGoods ordered (parts, accessories) are collected on site, at the garage address given in Article 1; no shipping is offered. Services are performed in the garage workshop, on the date agreed when booking or, failing that, within a reasonable period notified to the customer. The expected performance date is repeated in the order summary and in the confirmation e-mail, which constitutes the durable medium for the pre-contractual information (Article VI.45 §7 of the Code of Economic Law).\nArticle 7 — Right of withdrawal\nThe consumer has 14 days to withdraw, without having to give any reason. That period runs from the conclusion of the order. The request is submitted from the member area; the garage examines it and notifies its decision. If accepted, a credit note is issued and the refund is made using the same means of payment as the purchase.\nThe consumer may exercise the right of withdrawal without having to give any reason and without penalty (Article VI.47 of the Code of Economic Law). To do so, they notify the garage of their decision by an unambiguous statement (letter or e-mail to the contact details in Article 1), or by using the model withdrawal form made available to them, downloadable and reachable through a permanent link in their member area. The garage refunds all sums paid within fourteen days of receiving the withdrawal, using the same means of payment as the one used for the order, unless the consumer expressly agrees to another means. This right does not apply in the cases of exclusion set out in Article VI.53 of the Code of Economic Law, in particular the one described in Article 8 of these terms (services fully performed with prior express agreement).\nArticle 8 — Fully performed services\nWhere the customer asks for a service to be performed before the withdrawal period ends, they are offered the option of expressly acknowledging that they will lose that right once the service has been fully performed. This waiver is optional, is never pre-ticked, and evidence of it is retained. As long as the service has not been fully performed, the right of withdrawal remains.\nArticle 9 — Invoice\nAn invoice is issued for every paid order and made available as a PDF in the member area. Invoices are numbered continuously and without gaps, per financial year. They are kept for seven years, in accordance with value added tax legislation.\nArticle 10 — Legal guarantee of conformity\nFor goods sold to a consumer, the two-year legal guarantee of conformity applies (Articles 1649bis et seq. of the former Civil Code, now incorporated into Book 5 of the new Civil Code). This guarantee is owed by the seller, that is to say the garage. For services (servicing, repair, diagnosis, etc.), the garage is bound by an ordinary-law obligation of conformity: the service must match what was agreed; that obligation carries no fixed duration but remains framed by the rules on limitation periods. This legal guarantee applies irrespective of any commercial warranty.\nArticle 11 — Liability\nThe garage is liable for the proper performance of the services sold, under the conditions of the general law (Book 6 of the Civil Code, in force since 1 January 2025) and of these terms. The information published on the site is provided for guidance; the garage endeavours to keep it accurate and up to date without guaranteeing that it is exhaustive or free of error. The liability of the garage may in no case be excluded or limited in the event of gross negligence, fraud, harm to life or physical integrity, nor for mandatory legal obligations — in particular the legal guarantee of conformity and the safety of products and services. The garage assumes no liability for the content of third-party sites reachable through links, over which it exercises no control.\nArticle 12 — Personal data\nThe processing of personal data is described in the privacy policy, which is reachable from every page of the site.\nArticle 13 — Applicable law and disputes\nThese terms are governed by Belgian law. In the event of a dispute, the parties will seek an amicable solution before any legal action. The consumer is informed of the existence of the Consumer Mediation Service (Boulevard du Roi Albert II 8, 1000 Brussels — mediationconsommateur.be) and, for online purchases, of the European online dispute resolution platform (ec.europa.eu/consumers/odr). Failing an amicable settlement, the Belgian courts have jurisdiction; the consumer retains the benefit of the protective rules allowing them, where applicable, to bring proceedings before the court of their place of domicile.	e457e56ba594020e648d77ad0e5a552afd598209452dd3df71665794cd81efe6	f	2026-08-25 01:32:21.766689+00	2026-08-25 01:32:21.831384+00	systeme	systeme
4	CGV	CGV-2026-01	nl	2026-08-23 22:00:00+00	Algemene verkoopvoorwaarden\nDeze voorwaarden zijn van toepassing op de verkoop van onderdelen en werkplaatsdiensten via het platform AutoServ+.\nArtikel 1 — Verkoper\nDe verkoper is de hieronder vermelde garage, ingeschreven bij de Kruispuntbank van Ondernemingen en btw-plichtig.\nArtikel 2 — Toepassingsgebied en aanvaarding\nElke bestelling op het platform veronderstelt de voorafgaande en uitdrukkelijke aanvaarding van deze voorwaarden. Die aanvaarding wordt verzameld via een aankruisvakje op het besteloverzicht; zij wordt met tijdstempel bewaard, samen met de aanvaarde versie van het document en het gebruikte IP-adres.\nArtikel 3 — Prijzen en belasting over de toegevoegde waarde\nDe prijzen worden vermeld in euro, exclusief en inclusief belasting. De toegepaste tarieven zijn de Belgische tarieven van 0, 6, 12 en 21 %. De prijs, de omschrijving en het belastingtarief van een artikel worden vastgelegd op het ogenblik waarop het aan het winkelmandje wordt toegevoegd: een latere wijziging van de catalogus heeft geen invloed op een reeds samengesteld mandje.\nArtikel 4 — Bestelling\nDe bestelling komt in drie stappen tot stand: samenstelling van het mandje, overzicht met de details van de lijnen en de bedragen, en vervolgens bevestiging. De bevestigingsknop vermeldt uitdrukkelijk dat de bestelling een betalingsverplichting inhoudt. Een mandje kan uitsluitend onderdelen of uitsluitend diensten bevatten, nooit beide tegelijk.\nArtikel 5 — Betaling\nDe betaling verloopt online via een betaaldienstaanbieder. Er worden door de garage geen kaartgegevens verzameld of bewaard. De bestelling geldt pas als betaald na bevestiging van de status door de dienstverlener.\nArtikel 6 — Afhaling en uitvoering\nBestelde onderdelen worden in de garage afgehaald; er wordt geen verzending aangeboden. De online aankoop van een dienst reserveert geen tijdslot: de garage opent het werkplaatsdossier en spreekt de datum af met de klant.\nDe bestelde goederen (onderdelen, accessoires) worden ter plaatse afgehaald, op het adres van de garage vermeld in artikel 1; er wordt geen verzending aangeboden. De diensten worden uitgevoerd in de werkplaats van de garage, op de bij de reservatie afgesproken datum of, bij gebreke daarvan, binnen een redelijke termijn die aan de klant wordt meegedeeld. De voorziene uitvoeringsdatum wordt herhaald in het besteloverzicht en in de bevestigingsmail, die de duurzame gegevensdrager van de precontractuele informatie vormt (artikel VI.45 §7 van het Wetboek van economisch recht).\nArtikel 7 — Herroepingsrecht\nDe consument beschikt over een termijn van 14 dagen om zich te herroepen, zonder opgave van reden. Deze termijn loopt vanaf het sluiten van de bestelling. De aanvraag wordt ingediend vanuit de ledenruimte; de garage onderzoekt ze en deelt haar beslissing mee. Bij aanvaarding wordt een creditnota opgesteld en gebeurt de terugbetaling via hetzelfde betaalmiddel als bij de aankoop.\nDe consument kan zijn herroepingsrecht uitoefenen zonder opgave van reden en zonder boete (artikel VI.47 van het Wetboek van economisch recht). Om het uit te oefenen, deelt hij zijn beslissing aan de garage mee door middel van een ondubbelzinnige verklaring (brief of e-mail naar de contactgegevens van artikel 1), of met het modelformulier voor herroeping dat hem ter beschikking wordt gesteld, downloadbaar en toegankelijk via een permanente link in zijn ledenruimte. De garage betaalt alle gestorte bedragen terug binnen veertien dagen na ontvangst van de herroeping, met hetzelfde betaalmiddel als dat van de bestelling, tenzij de consument uitdrukkelijk met een ander middel instemt. Dit recht geldt niet in de uitsluitingsgevallen bepaald in artikel VI.53 van het Wetboek van economisch recht, met name het geval beschreven in artikel 8 van deze voorwaarden (volledig uitgevoerde diensten met voorafgaande uitdrukkelijke instemming).\nArtikel 8 — Volledig uitgevoerde diensten\nWanneer de klant vraagt dat een dienst wordt uitgevoerd vóór het verstrijken van de herroepingstermijn, wordt hem voorgesteld uitdrukkelijk te erkennen dat hij dat recht verliest zodra de dienst volledig is uitgevoerd. Die afstand is facultatief, wordt nooit vooraf aangevinkt, en het bewijs ervan wordt bewaard. Zolang de dienst niet volledig is uitgevoerd, blijft het herroepingsrecht bestaan.\nArtikel 9 — Factuur\nVoor elke betaalde bestelling wordt een factuur opgesteld en in pdf-formaat ter beschikking gesteld in de ledenruimte. De facturen worden per boekjaar doorlopend en zonder onderbreking genummerd. Zij worden zeven jaar bewaard, overeenkomstig de btw-wetgeving.\nArtikel 10 — Wettelijke conformiteitsgarantie\nVoor goederen die aan de consument worden verkocht, geldt de wettelijke conformiteitsgarantie van twee jaar (artikelen 1649bis en volgende van het oud Burgerlijk Wetboek, thans opgenomen in boek 5 van het nieuw Burgerlijk Wetboek). Deze garantie is verschuldigd door de verkoper, dat wil zeggen de garage. Voor de diensten (onderhoud, herstelling, diagnose, enz.) rust op de garage een gemeenrechtelijke conformiteitsverplichting: de dienst moet overeenstemmen met wat is overeengekomen; aan die verplichting is geen vaste duur verbonden, maar zij blijft omkaderd door de verjaringsregels. Deze wettelijke garantie geldt ongeacht enige commerciële waarborg.\nArtikel 11 — Aansprakelijkheid\nDe garage is aansprakelijk voor de goede uitvoering van de verkochte prestaties, onder de voorwaarden van het gemeen recht (boek 6 van het Burgerlijk Wetboek, in werking getreden op 1 januari 2025) en van deze voorwaarden. De op de site gepubliceerde informatie wordt ter indicatie verstrekt; de garage streeft ernaar ze juist en actueel te houden zonder de volledigheid ervan of de afwezigheid van fouten te waarborgen. De aansprakelijkheid van de garage kan in geen geval worden uitgesloten of beperkt in geval van zware fout, bedrog, aantasting van het leven of de lichamelijke integriteit, noch voor de dwingende wettelijke verplichtingen — met name de wettelijke conformiteitsgarantie en de veiligheid van producten en diensten. De garage draagt geen enkele aansprakelijkheid voor de inhoud van sites van derden die via links toegankelijk zijn en waarover zij geen enkele controle uitoefent.\nArtikel 12 — Persoonsgegevens\nDe verwerking van persoonsgegevens wordt beschreven in het privacybeleid, dat vanaf elke pagina van de site toegankelijk is.\nArtikel 13 — Toepasselijk recht en geschillen\nDeze voorwaarden worden beheerst door het Belgisch recht. Bij een geschil zoeken de partijen een minnelijke oplossing vóór elke gerechtelijke stap. De consument wordt ingelicht over het bestaan van de Consumentenombudsdienst (Koning Albert II-laan 8, 1000 Brussel — consumentenombudsdienst.be) en, voor onlineaankopen, over het Europese platform voor onlinegeschillenbeslechting (ec.europa.eu/consumers/odr). Bij gebrek aan een minnelijke regeling zijn de Belgische hoven en rechtbanken bevoegd; de consument behoudt het voordeel van de beschermende regels die hem in voorkomend geval toelaten de rechtbank van zijn woonplaats te vatten.	80420a435c1da0c51056cae257bfcfa8219ae5a0060107f51f96be65df91700a	f	2026-08-25 01:32:21.766689+00	2026-08-25 01:32:21.831384+00	systeme	systeme
1	CGV	CGV-2026-01	fr	2026-08-23 22:00:00+00	Conditions générales de vente\nLes présentes conditions régissent la vente de pièces et de prestations d'atelier par l'intermédiaire de la plateforme AutoServ+.\nArticle 1 — Vendeur\nLe vendeur est le garage identifié ci-dessous, inscrit à la Banque-Carrefour des Entreprises et assujetti à la taxe sur la valeur ajoutée.\nArticle 2 — Champ d'application et acceptation\nToute commande passée sur la plateforme suppose l'acceptation préalable et expresse des présentes conditions. Cette acceptation est recueillie par une case à cocher au récapitulatif de commande ; elle est horodatée et conservée avec la version du document acceptée et l'adresse IP utilisée.\nArticle 3 — Prix et taxe sur la valeur ajoutée\nLes prix sont indiqués en euros, hors taxe et taxe comprise. Les taux appliqués sont les taux belges de 0, 6, 12 et 21 %. Le prix, le libellé et le taux de taxe d'un article sont figés au moment où il est ajouté au panier : une modification ultérieure du catalogue reste sans effet sur un panier déjà constitué.\nArticle 4 — Commande\nLa commande se forme en trois temps : constitution du panier, récapitulatif reprenant le détail des lignes et des montants, puis validation. Le bouton de validation indique expressément que la commande oblige au paiement. Un panier ne peut contenir que des pièces ou que des prestations, jamais les deux à la fois.\nArticle 5 — Paiement\nLe paiement s'effectue en ligne auprès d'un prestataire de services de paiement. Aucune donnée de carte n'est collectée ni conservée par le garage. La commande n'est réputée payée qu'après confirmation du statut par le prestataire.\nArticle 6 — Retrait et exécution\nLes pièces commandées sont retirées au garage ; aucune expédition n'est proposée. L'achat en ligne d'une prestation ne réserve pas de créneau : le garage ouvre le dossier d'atelier et convient de la date avec le client.\nLes biens commandés (pièces, accessoires) sont retirés sur place, à l'adresse du garage indiquée à l'article 1 ; aucune expédition n'est proposée. Les prestations de service sont exécutées dans l'atelier du garage, à la date convenue lors de la réservation ou, à défaut, dans un délai raisonnable communiqué au client. La date d'exécution prévue est rappelée dans le récapitulatif de commande et dans l'e-mail de confirmation, qui constitue le support durable de l'information précontractuelle (article VI.45 §7 du Code de droit économique).\nArticle 7 — Droit de rétractation\nLe consommateur dispose d'un délai de 14 jours pour se rétracter, sans avoir à motiver sa décision. Ce délai court à compter de la conclusion de la commande. La demande se dépose depuis l'espace membre ; le garage l'examine et notifie sa décision. En cas d'acceptation, une note de crédit est émise et le remboursement intervient par le même moyen de paiement que celui employé lors de l'achat.\nLe consommateur peut exercer son droit de rétractation sans avoir à motiver sa décision et sans pénalité (article VI.47 du Code de droit économique). Pour l'exercer, il notifie sa décision au garage au moyen d'une déclaration dénuée d'ambiguïté (courrier postal ou courrier électronique aux coordonnées de l'article 1), ou en utilisant le formulaire type de rétractation mis à sa disposition, téléchargeable et accessible par un lien permanent dans son espace membre. Le garage rembourse la totalité des sommes versées dans les quatorze jours suivant la réception de la rétractation, en utilisant le même moyen de paiement que celui employé lors de la commande, sauf accord exprès du consommateur pour un autre moyen. Ce droit ne s'applique pas dans les cas d'exclusion prévus à l'article VI.53 du Code de droit économique, notamment celui décrit à l'article 8 des présentes (prestations pleinement exécutées avec accord préalable exprès).\nArticle 8 — Prestations pleinement exécutées\nLorsque le client demande l'exécution d'une prestation avant la fin du délai de rétractation, il lui est proposé de reconnaître expressément qu'il perdra ce droit une fois la prestation pleinement exécutée. Cette renonciation est facultative, n'est jamais pré-cochée, et sa preuve est conservée. Tant que la prestation n'est pas pleinement exécutée, le droit de rétractation subsiste.\nArticle 9 — Facture\nUne facture est émise pour toute commande payée et mise à disposition au format PDF dans l'espace membre. Les factures sont numérotées de manière continue et sans interruption, par exercice. Elles sont conservées sept ans, conformément à la législation relative à la taxe sur la valeur ajoutée.\nArticle 10 — Garantie légale de conformité\nPour les biens vendus au consommateur, la garantie légale de conformité de deux ans est applicable (articles 1649bis et suivants de l'ancien Code civil, désormais intégrés au livre 5 du nouveau Code civil). Cette garantie est due par le vendeur, c'est-à-dire le garage. Pour les prestations de service (entretien, réparation, diagnostic, etc.), le garage est tenu d'une obligation de conformité de droit commun : la prestation doit être conforme à ce qui a été convenu ; cette obligation n'est pas assortie d'une durée fixe mais reste encadrée par les règles de la prescription. La présente garantie légale s'applique indépendamment de toute garantie commerciale éventuelle.\nArticle 11 — Responsabilité\nLe garage est responsable de la bonne exécution des prestations vendues, dans les conditions du droit commun (livre 6 du Code civil, entré en vigueur le 1er janvier 2025) et des présentes conditions. Les informations publiées sur le site sont fournies à titre indicatif ; le garage s'efforce de les tenir exactes et à jour sans garantir leur exhaustivité ou l'absence d'erreur. La responsabilité du garage ne peut en aucun cas être exclue ou limitée en cas de faute lourde, de dol, d'atteinte à la vie ou à l'intégrité physique, ni pour les obligations légales impératives — notamment la garantie légale de conformité et la sécurité des produits et services. Le garage n'assume aucune responsabilité quant au contenu des sites tiers accessibles par des liens, sur lesquels il n'exerce aucun contrôle.\nArticle 12 — Données personnelles\nLe traitement des données personnelles est décrit dans la politique de confidentialité, accessible depuis chaque page du site.\nArticle 13 — Droit applicable et litiges\nLes présentes conditions sont régies par le droit belge. En cas de litige, les parties rechercheront une solution amiable avant toute action judiciaire. Le consommateur est informé de l'existence du Service de médiation pour le consommateur (Boulevard du Roi Albert II 8, 1000 Bruxelles — mediationconsommateur.be) et, pour les achats en ligne, de la plateforme européenne de règlement en ligne des litiges (ec.europa.eu/consumers/odr). À défaut de règlement amiable, les cours et tribunaux belges sont compétents ; le consommateur conserve le bénéfice des règles protectrices lui permettant, le cas échéant, de saisir la juridiction de son lieu de domicile.	5fb51347674b397f16267f2e0588a6c7f54634aa182c7e62ecf49b52f91d92af	f	2026-08-25 01:32:21.766689+00	2026-08-25 01:32:21.831384+00	systeme	systeme
\.


--
-- Name: avis_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.avis_id_seq', 1, true);


--
-- Name: avoir_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.avoir_id_seq', 1, false);


--
-- Name: categorie_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.categorie_id_seq', 11, true);


--
-- Name: clef_api_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.clef_api_id_seq', 1, false);


--
-- Name: commande_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.commande_id_seq', 1, true);


--
-- Name: consentement_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.consentement_id_seq', 3, true);


--
-- Name: conversation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.conversation_id_seq', 1, true);


--
-- Name: demande_annulation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.demande_annulation_id_seq', 1, false);


--
-- Name: facture_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.facture_id_seq', 1, true);


--
-- Name: historique_modification_catalogue_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.historique_modification_catalogue_id_seq', 1, false);


--
-- Name: historique_statut_intervention_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.historique_statut_intervention_id_seq', 13, true);


--
-- Name: indisponibilite_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.indisponibilite_id_seq', 1, false);


--
-- Name: intervention_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.intervention_id_seq', 6, true);


--
-- Name: ligne_intervention_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.ligne_intervention_id_seq', 8, true);


--
-- Name: ligne_panier_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.ligne_panier_id_seq', 2, true);


--
-- Name: message_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.message_id_seq', 2, true);


--
-- Name: notification_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notification_id_seq', 4, true);


--
-- Name: paiement_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.paiement_id_seq', 1, true);


--
-- Name: panier_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.panier_id_seq', 1, true);


--
-- Name: photo_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.photo_id_seq', 1, false);


--
-- Name: piece_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.piece_id_seq', 6, true);


--
-- Name: place_parking_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.place_parking_id_seq', 8, true);


--
-- Name: plage_ouverture_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.plage_ouverture_id_seq', 11, true);


--
-- Name: poste_atelier_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.poste_atelier_id_seq', 3, true);


--
-- Name: rdv_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.rdv_id_seq', 6, true);


--
-- Name: rdv_numero_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.rdv_numero_seq', 1, false);


--
-- Name: rdv_service_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.rdv_service_id_seq', 2, true);


--
-- Name: reservation_parking_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reservation_parking_id_seq', 1, false);


--
-- Name: seq_numero_avoir; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.seq_numero_avoir', 1, false);


--
-- Name: seq_numero_commande; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.seq_numero_commande', 1, true);


--
-- Name: seq_numero_facture; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.seq_numero_facture', 1, false);


--
-- Name: seq_numero_intervention; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.seq_numero_intervention', 6, true);


--
-- Name: seq_numero_parking; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.seq_numero_parking', 1, false);


--
-- Name: seq_numero_rdv; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.seq_numero_rdv', 6, true);


--
-- Name: service_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.service_id_seq', 9, true);


--
-- Name: utilisateur_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.utilisateur_id_seq', 2, true);


--
-- Name: vehicule_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.vehicule_id_seq', 2, true);


--
-- Name: version_document_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.version_document_id_seq', 12, true);


--
-- Name: avis avis_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avis
    ADD CONSTRAINT avis_pkey PRIMARY KEY (id);


--
-- Name: avoir avoir_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avoir
    ADD CONSTRAINT avoir_pkey PRIMARY KEY (id);


--
-- Name: categorie categorie_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categorie
    ADD CONSTRAINT categorie_pkey PRIMARY KEY (id);


--
-- Name: clef_api clef_api_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clef_api
    ADD CONSTRAINT clef_api_pkey PRIMARY KEY (id);


--
-- Name: commande commande_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commande
    ADD CONSTRAINT commande_pkey PRIMARY KEY (id);


--
-- Name: compteur_avoir compteur_avoir_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compteur_avoir
    ADD CONSTRAINT compteur_avoir_pkey PRIMARY KEY (exercice);


--
-- Name: compteur_facture compteur_facture_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compteur_facture
    ADD CONSTRAINT compteur_facture_pkey PRIMARY KEY (exercice);


--
-- Name: consentement consentement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consentement
    ADD CONSTRAINT consentement_pkey PRIMARY KEY (id);


--
-- Name: conversation conversation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation
    ADD CONSTRAINT conversation_pkey PRIMARY KEY (id);


--
-- Name: demande_annulation demande_annulation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demande_annulation
    ADD CONSTRAINT demande_annulation_pkey PRIMARY KEY (id);


--
-- Name: plage_ouverture ex_plage_ouverture_chevauchement; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plage_ouverture
    ADD CONSTRAINT ex_plage_ouverture_chevauchement EXCLUDE USING gist (jour_semaine WITH =, tsrange(('2000-01-01'::date + heure_debut), ('2000-01-01'::date + heure_fin), '[)'::text) WITH &&) WHERE ((deleted_at IS NULL));


--
-- Name: rdv ex_rdv_poste_intervalle; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv
    ADD CONSTRAINT ex_rdv_poste_intervalle EXCLUDE USING gist (poste_id WITH =, tstzrange(debut, fin, '[)'::text) WITH &&) WHERE (((deleted_at IS NULL) AND ((statut)::text = ANY ((ARRAY['EN_ATTENTE'::character varying, 'CONFIRME'::character varying])::text[]))));


--
-- Name: facture facture_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facture
    ADD CONSTRAINT facture_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: historique_modification_catalogue historique_modification_catalogue_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_modification_catalogue
    ADD CONSTRAINT historique_modification_catalogue_pkey PRIMARY KEY (id);


--
-- Name: historique_statut_intervention historique_statut_intervention_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_statut_intervention
    ADD CONSTRAINT historique_statut_intervention_pkey PRIMARY KEY (id);


--
-- Name: indisponibilite indisponibilite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indisponibilite
    ADD CONSTRAINT indisponibilite_pkey PRIMARY KEY (id);


--
-- Name: intervention intervention_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intervention
    ADD CONSTRAINT intervention_pkey PRIMARY KEY (id);


--
-- Name: ligne_intervention ligne_intervention_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ligne_intervention
    ADD CONSTRAINT ligne_intervention_pkey PRIMARY KEY (id);


--
-- Name: ligne_panier ligne_panier_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ligne_panier
    ADD CONSTRAINT ligne_panier_pkey PRIMARY KEY (id);


--
-- Name: message message_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message
    ADD CONSTRAINT message_pkey PRIMARY KEY (id);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: paiement paiement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.paiement
    ADD CONSTRAINT paiement_pkey PRIMARY KEY (id);


--
-- Name: panier panier_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.panier
    ADD CONSTRAINT panier_pkey PRIMARY KEY (id);


--
-- Name: parametre_atelier parametre_atelier_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parametre_atelier
    ADD CONSTRAINT parametre_atelier_pkey PRIMARY KEY (id);


--
-- Name: photo photo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.photo
    ADD CONSTRAINT photo_pkey PRIMARY KEY (id);


--
-- Name: piece piece_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.piece
    ADD CONSTRAINT piece_pkey PRIMARY KEY (id);


--
-- Name: place_parking place_parking_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_parking
    ADD CONSTRAINT place_parking_pkey PRIMARY KEY (id);


--
-- Name: plage_ouverture plage_ouverture_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plage_ouverture
    ADD CONSTRAINT plage_ouverture_pkey PRIMARY KEY (id);


--
-- Name: poste_atelier poste_atelier_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.poste_atelier
    ADD CONSTRAINT poste_atelier_pkey PRIMARY KEY (id);


--
-- Name: rdv rdv_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv
    ADD CONSTRAINT rdv_pkey PRIMARY KEY (id);


--
-- Name: rdv_service rdv_service_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv_service
    ADD CONSTRAINT rdv_service_pkey PRIMARY KEY (id);


--
-- Name: reservation_parking reservation_parking_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_parking
    ADD CONSTRAINT reservation_parking_pkey PRIMARY KEY (id);


--
-- Name: service service_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service
    ADD CONSTRAINT service_pkey PRIMARY KEY (id);


--
-- Name: avis uq_avis_intervention; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avis
    ADD CONSTRAINT uq_avis_intervention UNIQUE (intervention_id);


--
-- Name: CONSTRAINT uq_avis_intervention ON avis; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT uq_avis_intervention ON public.avis IS 'Un seul avis par intervention : garantit l authenticite des avis publies.';


--
-- Name: avis uq_avis_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avis
    ADD CONSTRAINT uq_avis_reference UNIQUE (reference);


--
-- Name: avoir uq_avoir_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avoir
    ADD CONSTRAINT uq_avoir_numero UNIQUE (numero);


--
-- Name: avoir uq_avoir_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avoir
    ADD CONSTRAINT uq_avoir_reference UNIQUE (reference);


--
-- Name: categorie uq_categorie_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categorie
    ADD CONSTRAINT uq_categorie_code UNIQUE (code);


--
-- Name: clef_api uq_clef_api_hachee; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clef_api
    ADD CONSTRAINT uq_clef_api_hachee UNIQUE (clef_hachee);


--
-- Name: clef_api uq_clef_api_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clef_api
    ADD CONSTRAINT uq_clef_api_reference UNIQUE (reference);


--
-- Name: commande uq_commande_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commande
    ADD CONSTRAINT uq_commande_numero UNIQUE (numero);


--
-- Name: commande uq_commande_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commande
    ADD CONSTRAINT uq_commande_reference UNIQUE (reference);


--
-- Name: conversation uq_conversation_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation
    ADD CONSTRAINT uq_conversation_reference UNIQUE (reference);


--
-- Name: demande_annulation uq_demande_annulation_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demande_annulation
    ADD CONSTRAINT uq_demande_annulation_reference UNIQUE (reference);


--
-- Name: facture uq_facture_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facture
    ADD CONSTRAINT uq_facture_numero UNIQUE (numero);


--
-- Name: facture uq_facture_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facture
    ADD CONSTRAINT uq_facture_reference UNIQUE (reference);


--
-- Name: facture uq_facture_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facture
    ADD CONSTRAINT uq_facture_sequence UNIQUE (exercice, sequence_annuelle);


--
-- Name: CONSTRAINT uq_facture_sequence ON facture; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT uq_facture_sequence ON public.facture IS 'Garantit une numerotation continue par exercice comptable.';


--
-- Name: indisponibilite uq_indispo_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indisponibilite
    ADD CONSTRAINT uq_indispo_reference UNIQUE (reference);


--
-- Name: intervention uq_intervention_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intervention
    ADD CONSTRAINT uq_intervention_numero UNIQUE (numero);


--
-- Name: intervention uq_intervention_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intervention
    ADD CONSTRAINT uq_intervention_reference UNIQUE (reference);


--
-- Name: paiement uq_paiement_idempotence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.paiement
    ADD CONSTRAINT uq_paiement_idempotence UNIQUE (cle_idempotence);


--
-- Name: paiement uq_paiement_mollie; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.paiement
    ADD CONSTRAINT uq_paiement_mollie UNIQUE (reference_mollie);


--
-- Name: paiement uq_paiement_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.paiement
    ADD CONSTRAINT uq_paiement_reference UNIQUE (reference);


--
-- Name: panier uq_panier_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.panier
    ADD CONSTRAINT uq_panier_reference UNIQUE (reference);


--
-- Name: piece uq_piece_fabricant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.piece
    ADD CONSTRAINT uq_piece_fabricant UNIQUE (reference_fabricant);


--
-- Name: piece uq_piece_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.piece
    ADD CONSTRAINT uq_piece_reference UNIQUE (reference);


--
-- Name: place_parking uq_place_parking_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.place_parking
    ADD CONSTRAINT uq_place_parking_numero UNIQUE (numero);


--
-- Name: poste_atelier uq_poste_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.poste_atelier
    ADD CONSTRAINT uq_poste_reference UNIQUE (reference);


--
-- Name: rdv uq_rdv_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv
    ADD CONSTRAINT uq_rdv_numero UNIQUE (numero);


--
-- Name: rdv uq_rdv_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv
    ADD CONSTRAINT uq_rdv_reference UNIQUE (reference);


--
-- Name: rdv_service uq_rdv_service; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv_service
    ADD CONSTRAINT uq_rdv_service UNIQUE (rdv_id, service_id);


--
-- Name: reservation_parking uq_resa_parking_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_parking
    ADD CONSTRAINT uq_resa_parking_numero UNIQUE (numero);


--
-- Name: reservation_parking uq_resa_parking_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_parking
    ADD CONSTRAINT uq_resa_parking_reference UNIQUE (reference);


--
-- Name: service uq_service_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service
    ADD CONSTRAINT uq_service_code UNIQUE (code);


--
-- Name: service uq_service_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service
    ADD CONSTRAINT uq_service_reference UNIQUE (reference);


--
-- Name: utilisateur uq_utilisateur_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utilisateur
    ADD CONSTRAINT uq_utilisateur_email UNIQUE (email);


--
-- Name: utilisateur uq_utilisateur_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utilisateur
    ADD CONSTRAINT uq_utilisateur_reference UNIQUE (reference);


--
-- Name: vehicule uq_vehicule_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicule
    ADD CONSTRAINT uq_vehicule_reference UNIQUE (reference);


--
-- Name: version_document uq_version_document; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.version_document
    ADD CONSTRAINT uq_version_document UNIQUE (type_document, version, langue);


--
-- Name: utilisateur utilisateur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utilisateur
    ADD CONSTRAINT utilisateur_pkey PRIMARY KEY (id);


--
-- Name: vehicule vehicule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicule
    ADD CONSTRAINT vehicule_pkey PRIMARY KEY (id);


--
-- Name: version_document version_document_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.version_document
    ADD CONSTRAINT version_document_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: ix_categorie_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_categorie_type ON public.categorie USING btree (type) WHERE (deleted_at IS NULL);


--
-- Name: ix_commande_membre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_commande_membre ON public.commande USING btree (membre_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_commande_statut; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_commande_statut ON public.commande USING btree (statut) WHERE (deleted_at IS NULL);


--
-- Name: ix_consentement_utilisateur; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_consentement_utilisateur ON public.consentement USING btree (utilisateur_id);


--
-- Name: ix_conversation_membre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_conversation_membre ON public.conversation USING btree (membre_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_demande_annulation_commande; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_demande_annulation_commande ON public.demande_annulation USING btree (commande_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_demande_annulation_statut; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_demande_annulation_statut ON public.demande_annulation USING btree (statut) WHERE (deleted_at IS NULL);


--
-- Name: ix_facture_emission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_facture_emission ON public.facture USING btree (date_emission);


--
-- Name: ix_facture_membre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_facture_membre ON public.facture USING btree (membre_id);


--
-- Name: ix_historique_modification_catalogue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historique_modification_catalogue ON public.historique_modification_catalogue USING btree (type_entite, entite_id, horodatage);


--
-- Name: ix_historique_statut_intervention; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_historique_statut_intervention ON public.historique_statut_intervention USING btree (intervention_id, horodatage);


--
-- Name: ix_indispo_intervalle; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_indispo_intervalle ON public.indisponibilite USING gist (tstzrange(debut, fin, '[)'::text)) WHERE (deleted_at IS NULL);


--
-- Name: ix_intervention_commande; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_intervention_commande ON public.intervention USING btree (commande_id) WHERE (commande_id IS NOT NULL);


--
-- Name: ix_intervention_rdv; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_intervention_rdv ON public.intervention USING btree (rdv_id) WHERE (rdv_id IS NOT NULL);


--
-- Name: ix_intervention_statut; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_intervention_statut ON public.intervention USING btree (statut) WHERE (deleted_at IS NULL);


--
-- Name: ix_intervention_vehicule; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_intervention_vehicule ON public.intervention USING btree (vehicule_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_ligne_intervention; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_ligne_intervention ON public.ligne_intervention USING btree (intervention_id);


--
-- Name: ix_ligne_panier_commande; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_ligne_panier_commande ON public.ligne_panier USING btree (commande_id) WHERE (commande_id IS NOT NULL);


--
-- Name: ix_ligne_panier_panier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_ligne_panier_panier ON public.ligne_panier USING btree (panier_id) WHERE (panier_id IS NOT NULL);


--
-- Name: ix_message_conversation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_message_conversation ON public.message USING btree (conversation_id, date_envoi) WHERE (deleted_at IS NULL);


--
-- Name: ix_notification_membre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_notification_membre ON public.notification USING btree (membre_id, statut) WHERE (deleted_at IS NULL);


--
-- Name: ix_paiement_commande; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_paiement_commande ON public.paiement USING btree (commande_id) WHERE (commande_id IS NOT NULL);


--
-- Name: ix_paiement_statut; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_paiement_statut ON public.paiement USING btree (statut);


--
-- Name: ix_photo_intervention; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_photo_intervention ON public.photo USING btree (intervention_id) WHERE (intervention_id IS NOT NULL);


--
-- Name: ix_photo_piece; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_photo_piece ON public.photo USING btree (piece_id) WHERE (piece_id IS NOT NULL);


--
-- Name: ix_photo_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_photo_service ON public.photo USING btree (service_id) WHERE (service_id IS NOT NULL);


--
-- Name: ix_piece_categorie; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_piece_categorie ON public.piece USING btree (categorie_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_rdv_membre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_rdv_membre ON public.rdv USING btree (membre_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_rdv_poste_debut; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_rdv_poste_debut ON public.rdv USING btree (poste_id, debut) WHERE (deleted_at IS NULL);


--
-- Name: ix_rdv_statut; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_rdv_statut ON public.rdv USING btree (statut) WHERE (deleted_at IS NULL);


--
-- Name: ix_resa_parking_membre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_resa_parking_membre ON public.reservation_parking USING btree (membre_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_resa_parking_place; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_resa_parking_place ON public.reservation_parking USING btree (place_id, date_debut, date_fin) WHERE (deleted_at IS NULL);


--
-- Name: ix_service_actif; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_service_actif ON public.service USING btree (actif) WHERE (deleted_at IS NULL);


--
-- Name: ix_service_categorie; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_service_categorie ON public.service USING btree (categorie_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_utilisateur_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_utilisateur_email ON public.utilisateur USING btree (email) WHERE (deleted_at IS NULL);


--
-- Name: ix_utilisateur_jeton; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_utilisateur_jeton ON public.utilisateur USING btree (jeton_verification) WHERE (jeton_verification IS NOT NULL);


--
-- Name: ix_utilisateur_statut; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_utilisateur_statut ON public.utilisateur USING btree (statut) WHERE (deleted_at IS NULL);


--
-- Name: ix_vehicule_membre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_vehicule_membre ON public.vehicule USING btree (membre_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_version_document_en_vigueur; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_version_document_en_vigueur ON public.version_document USING btree (type_document, actif, date_effet DESC, id DESC);


--
-- Name: uq_avoir_facture; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_avoir_facture ON public.avoir USING btree (facture_id);


--
-- Name: INDEX uq_avoir_facture; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.uq_avoir_facture IS 'Une facture, au plus un avoir (perimetre V1 : annulation totale). Garde d idempotence contre deux validations simultanees, et enonce du perimetre : l annulation partielle par ligne (V2) devra lever cette unicite.';


--
-- Name: uq_demande_annulation_en_attente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_demande_annulation_en_attente ON public.demande_annulation USING btree (commande_id) WHERE (((statut)::text = 'EN_ATTENTE'::text) AND (deleted_at IS NULL));


--
-- Name: uq_facture_commande; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_facture_commande ON public.facture USING btree (commande_id) WHERE (commande_id IS NOT NULL);


--
-- Name: uq_facture_intervention; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_facture_intervention ON public.facture USING btree (intervention_id) WHERE (intervention_id IS NOT NULL);


--
-- Name: uq_paiement_remboursement; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_paiement_remboursement ON public.paiement USING btree (reference_remboursement) WHERE (reference_remboursement IS NOT NULL);


--
-- Name: uq_panier_membre_actif; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_panier_membre_actif ON public.panier USING btree (membre_id) WHERE (deleted_at IS NULL);


--
-- Name: INDEX uq_panier_membre_actif; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.uq_panier_membre_actif IS 'Un seul panier ouvert par membre a la fois.';


--
-- Name: uq_poste_libelle_actif; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_poste_libelle_actif ON public.poste_atelier USING btree (lower((libelle)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uq_vehicule_plaque_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_vehicule_plaque_active ON public.vehicule USING btree (plaque) WHERE (deleted_at IS NULL);


--
-- Name: avoir tg_avoir_immuable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tg_avoir_immuable BEFORE UPDATE ON public.avoir FOR EACH ROW EXECUTE FUNCTION public.fn_avoir_immuable();


--
-- Name: facture tg_facture_immuable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tg_facture_immuable BEFORE UPDATE ON public.facture FOR EACH ROW EXECUTE FUNCTION public.fn_facture_immuable();


--
-- Name: avis fk_avis_intervention; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avis
    ADD CONSTRAINT fk_avis_intervention FOREIGN KEY (intervention_id) REFERENCES public.intervention(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: avis fk_avis_membre; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avis
    ADD CONSTRAINT fk_avis_membre FOREIGN KEY (membre_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: avoir fk_avoir_facture; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.avoir
    ADD CONSTRAINT fk_avoir_facture FOREIGN KEY (facture_id) REFERENCES public.facture(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: commande fk_commande_membre; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commande
    ADD CONSTRAINT fk_commande_membre FOREIGN KEY (membre_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: consentement fk_consentement_utilisateur; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consentement
    ADD CONSTRAINT fk_consentement_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: conversation fk_conversation_interv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation
    ADD CONSTRAINT fk_conversation_interv FOREIGN KEY (intervention_id) REFERENCES public.intervention(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: conversation fk_conversation_membre; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation
    ADD CONSTRAINT fk_conversation_membre FOREIGN KEY (membre_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: demande_annulation fk_demande_annulation_avoir; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demande_annulation
    ADD CONSTRAINT fk_demande_annulation_avoir FOREIGN KEY (avoir_id) REFERENCES public.avoir(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: demande_annulation fk_demande_annulation_commande; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demande_annulation
    ADD CONSTRAINT fk_demande_annulation_commande FOREIGN KEY (commande_id) REFERENCES public.commande(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: demande_annulation fk_demande_annulation_decideur; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.demande_annulation
    ADD CONSTRAINT fk_demande_annulation_decideur FOREIGN KEY (decide_par) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: facture fk_facture_commande; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facture
    ADD CONSTRAINT fk_facture_commande FOREIGN KEY (commande_id) REFERENCES public.commande(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: facture fk_facture_intervention; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facture
    ADD CONSTRAINT fk_facture_intervention FOREIGN KEY (intervention_id) REFERENCES public.intervention(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: facture fk_facture_membre; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facture
    ADD CONSTRAINT fk_facture_membre FOREIGN KEY (membre_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: historique_modification_catalogue fk_histo_catalogue_auteur; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_modification_catalogue
    ADD CONSTRAINT fk_histo_catalogue_auteur FOREIGN KEY (auteur_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: historique_statut_intervention fk_histo_statut_auteur; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_statut_intervention
    ADD CONSTRAINT fk_histo_statut_auteur FOREIGN KEY (auteur_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: historique_statut_intervention fk_histo_statut_interv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_statut_intervention
    ADD CONSTRAINT fk_histo_statut_interv FOREIGN KEY (intervention_id) REFERENCES public.intervention(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: intervention fk_intervention_commande; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intervention
    ADD CONSTRAINT fk_intervention_commande FOREIGN KEY (commande_id) REFERENCES public.commande(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: intervention fk_intervention_rdv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intervention
    ADD CONSTRAINT fk_intervention_rdv FOREIGN KEY (rdv_id) REFERENCES public.rdv(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: intervention fk_intervention_vehicule; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intervention
    ADD CONSTRAINT fk_intervention_vehicule FOREIGN KEY (vehicule_id) REFERENCES public.vehicule(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: ligne_intervention fk_ligne_interv_interv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ligne_intervention
    ADD CONSTRAINT fk_ligne_interv_interv FOREIGN KEY (intervention_id) REFERENCES public.intervention(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: ligne_intervention fk_ligne_interv_piece; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ligne_intervention
    ADD CONSTRAINT fk_ligne_interv_piece FOREIGN KEY (piece_id) REFERENCES public.piece(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: ligne_intervention fk_ligne_interv_service; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ligne_intervention
    ADD CONSTRAINT fk_ligne_interv_service FOREIGN KEY (service_id) REFERENCES public.service(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: ligne_panier fk_ligne_panier_commande; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ligne_panier
    ADD CONSTRAINT fk_ligne_panier_commande FOREIGN KEY (commande_id) REFERENCES public.commande(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: ligne_panier fk_ligne_panier_panier; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ligne_panier
    ADD CONSTRAINT fk_ligne_panier_panier FOREIGN KEY (panier_id) REFERENCES public.panier(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: ligne_panier fk_ligne_panier_piece; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ligne_panier
    ADD CONSTRAINT fk_ligne_panier_piece FOREIGN KEY (piece_id) REFERENCES public.piece(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: ligne_panier fk_ligne_panier_service; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ligne_panier
    ADD CONSTRAINT fk_ligne_panier_service FOREIGN KEY (service_id) REFERENCES public.service(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: message fk_message_conversation; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message
    ADD CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES public.conversation(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: message fk_message_expediteur; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message
    ADD CONSTRAINT fk_message_expediteur FOREIGN KEY (expediteur_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: notification fk_notification_membre; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT fk_notification_membre FOREIGN KEY (membre_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: paiement fk_paiement_commande; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.paiement
    ADD CONSTRAINT fk_paiement_commande FOREIGN KEY (commande_id) REFERENCES public.commande(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: panier fk_panier_membre; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.panier
    ADD CONSTRAINT fk_panier_membre FOREIGN KEY (membre_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: photo fk_photo_intervention; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.photo
    ADD CONSTRAINT fk_photo_intervention FOREIGN KEY (intervention_id) REFERENCES public.intervention(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: photo fk_photo_piece; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.photo
    ADD CONSTRAINT fk_photo_piece FOREIGN KEY (piece_id) REFERENCES public.piece(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: photo fk_photo_service; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.photo
    ADD CONSTRAINT fk_photo_service FOREIGN KEY (service_id) REFERENCES public.service(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: piece fk_piece_categorie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.piece
    ADD CONSTRAINT fk_piece_categorie FOREIGN KEY (categorie_id) REFERENCES public.categorie(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: rdv fk_rdv_membre; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv
    ADD CONSTRAINT fk_rdv_membre FOREIGN KEY (membre_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: rdv_service fk_rdv_service_rdv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv_service
    ADD CONSTRAINT fk_rdv_service_rdv FOREIGN KEY (rdv_id) REFERENCES public.rdv(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: rdv_service fk_rdv_service_svc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv_service
    ADD CONSTRAINT fk_rdv_service_svc FOREIGN KEY (service_id) REFERENCES public.service(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: rdv fk_rdv_vehicule; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv
    ADD CONSTRAINT fk_rdv_vehicule FOREIGN KEY (vehicule_id) REFERENCES public.vehicule(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: reservation_parking fk_resa_parking_membre; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_parking
    ADD CONSTRAINT fk_resa_parking_membre FOREIGN KEY (membre_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: reservation_parking fk_resa_parking_paiement; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_parking
    ADD CONSTRAINT fk_resa_parking_paiement FOREIGN KEY (paiement_id) REFERENCES public.paiement(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: reservation_parking fk_resa_parking_place; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_parking
    ADD CONSTRAINT fk_resa_parking_place FOREIGN KEY (place_id) REFERENCES public.place_parking(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: reservation_parking fk_resa_parking_vehicule; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_parking
    ADD CONSTRAINT fk_resa_parking_vehicule FOREIGN KEY (vehicule_id) REFERENCES public.vehicule(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: service fk_service_categorie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service
    ADD CONSTRAINT fk_service_categorie FOREIGN KEY (categorie_id) REFERENCES public.categorie(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: vehicule fk_vehicule_membre; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicule
    ADD CONSTRAINT fk_vehicule_membre FOREIGN KEY (membre_id) REFERENCES public.utilisateur(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: indisponibilite indisponibilite_poste_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.indisponibilite
    ADD CONSTRAINT indisponibilite_poste_id_fkey FOREIGN KEY (poste_id) REFERENCES public.poste_atelier(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: rdv rdv_poste_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rdv
    ADD CONSTRAINT rdv_poste_id_fkey FOREIGN KEY (poste_id) REFERENCES public.poste_atelier(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--

\unrestrict ENpDnZKxlRPB6NWKBBfJlxn1hG5xGpMmqMX6M6akgBTfuSScBRwwPq4LX6Br4K8

