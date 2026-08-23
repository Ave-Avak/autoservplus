-- =====================================================================================
-- Suppression de compte par anonymisation (F23, RM-05 — article 17 RGPD).
--
-- 1) POURQUOI ANONYMISER PLUTOT QUE SUPPRIMER
--
--    Le droit a l effacement n est pas absolu : l article 17.3.b RGPD ecarte
--    l obligation d effacer lorsque le traitement est necessaire au respect d une
--    obligation legale. Le Code de la TVA (art. 60) impose la conservation des
--    factures pendant sept ans, et une facture doit legalement porter l identite du
--    client (AR n°1, art. 5). Les deux exigences ne s opposent pas : elles ne
--    portent pas sur le meme objet. Le document comptable est conserve intact ; la
--    ligne applicative qui identifie la personne est videe.
--
--    Concretement, l anonymisation ne touche qu une table de donnees : utilisateur.
--    Les tables facture, avoir et paiement ne stockent AUCUN nom — seulement des
--    montants et une cle etrangere vers commande, puis vers utilisateur. Le « Client
--    supprime » du CdC n est donc pas un champ a reecrire sur chaque document : c est
--    l effet automatique de l anonymisation de la ligne utilisateur, lue par relation.
--
--    Les PDF deja archives, eux, portent le nom en dur, fige a l emission. Ils ne
--    sont ni regeneres ni modifies : ce sont precisement les documents que la loi
--    fiscale ordonne de conserver en l etat.
--
-- 2) anonymise_le : UN MARQUEUR, PAS UNE SUPPRESSION LOGIQUE
--
--    L entite Utilisateur porte @SQLRestriction("deleted_at IS NULL"). Renseigner
--    deleted_at a l anonymisation masquerait la ligne de TOUTES les requetes de
--    l application — y compris de la jointure par laquelle une facture resout son
--    titulaire. Les documents comptables conserves afficheraient alors un vide au
--    lieu de « Client supprime », et la relation JPA casserait.
--
--    La ligne doit donc rester vivante et jointe. anonymise_le dit « cette ligne ne
--    designe plus personne » sans la retirer du champ de vision : c est un etat, pas
--    une disparition. deleted_at reste vide, et le statut passe a SUPPRIME — valeur
--    deja admise par ck_utilisateur_statut (V1) et deja presente dans l enum
--    StatutUtilisateur : il n y a rien a elargir.
--
--    TIMESTAMPTZ et non TIMESTAMP : tout l horodatage du schema est en TIMESTAMPTZ,
--    melanger les deux ferait dependre la lecture du fuseau de la session.
--
-- 3) LE BALAYAGE DES COLONNES D AUDIT, ET POURQUOI SA LISTE EST ECRITE EN TOUTES LETTRES
--
--    created_by et updated_by sont alimentees par JpaAuditingConfig avec
--    auth.getName(), c est-a-dire l ADRESSE DE COURRIEL en clair. Anonymiser la
--    seule table utilisateur laisserait donc l adresse reelle, lisible, dans une
--    dizaine de tables : vehicule, rdv, panier, ligne_panier, commande,
--    consentement, paiement, demande_annulation. Un effacement qui laisse
--    l identifiant de connexion en clair n en est pas un.
--
--    La liste des colonnes balayees est ENUMEREE, pas calculee. Une premiere version
--    la derivait d information_schema au moment de l execution ; c etait commode mais
--    indefendable : une operation legale d effacement doit pouvoir enoncer exactement
--    quelles colonnes de quelles tables sont ecrasees, et une liste calculee au
--    runtime ne s enonce pas — elle depend de l etat de la base a l instant de
--    l appel. fn_tables_traces_audit() est donc la declaration auditable : un
--    SELECT dessus repond a la question, aujourd hui comme dans trois ans, et la
--    reponse ne change pas entre deux executions.
--
--    Le risque de la liste figee — une table ajoutee plus tard, oubliee, qui ferait
--    fuiter en silence — est couvert par un test d integration : SchemaIT confronte
--    cette liste a l ensemble reel des colonnes d audit du schema et ECHOUE si l une
--    manque. La build casse au lieu de laisser passer. C est la meme garantie que le
--    calcul dynamique offrait, mais au moment ou l on peut encore agir plutot qu au
--    moment de l effacement.
--
--    La liste couvre TOUTES les tables portant une colonne d audit, y compris celles
--    qu un membre ne cree jamais. L exhaustivite se prouve ; un sous-ensemble
--    « pertinent » demanderait de justifier chaque exclusion, et vieillirait mal.
--
--    Elle touche AUSSI facture et avoir, et c est voulu : created_by n est pas une
--    mention legale de la facture. Les mentions obligatoires sont le numero, la date,
--    les montants et l identite des parties — jamais la trace applicative de qui a
--    clique. Verification faite sur les deux triggers : tg_facture_immuable garde
--    numero, montants, taux et date d emission ; tg_avoir_immuable garde numero,
--    facture_id, montants, motif et date d emission. Ni l un ni l autre ne garde
--    created_by, updated_by ou chemin_pdf. Le balayage passe donc, et le contenu
--    comptable reste protege exactement comme avant.
-- =====================================================================================

ALTER TABLE utilisateur ADD COLUMN anonymise_le TIMESTAMPTZ;

COMMENT ON COLUMN utilisateur.anonymise_le IS
    'Horodatage de l anonymisation du compte (F23, art. 17 RGPD). Marqueur d etat et '
    'NON suppression logique : deleted_at doit rester vide, sans quoi le SQLRestriction '
    'de l entite masquerait la ligne et les factures conservees ne pourraient plus '
    'resoudre leur titulaire. La ligne survit, videe de toute donnee personnelle.';

-- --- declaration auditable des colonnes balayees ---------------------------------------

CREATE OR REPLACE FUNCTION fn_tables_traces_audit()
    RETURNS TABLE (nom_table TEXT, nom_colonne TEXT) AS $$
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
        ('vehicule', 'updated_by');
$$ LANGUAGE sql IMMUTABLE;

COMMENT ON FUNCTION fn_tables_traces_audit() IS
    'Liste EXPLICITE des colonnes d audit balayees a l anonymisation d un compte (F23). '
    'Enumeree et non derivee du catalogue : un effacement legal doit pouvoir enoncer '
    'exactement ce qu il ecrase, et la reponse ne doit pas dependre de l etat de la base '
    'au moment de l appel. Un SELECT sur cette fonction est la reponse auditable. '
    'SchemaIT confronte cette liste au schema reel et echoue si une colonne manque : '
    'une table ajoutee plus tard casse la build au lieu de fuiter en silence.';

-- --- balayage proprement dit -----------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_anonymiser_traces_audit(ancien TEXT, jeton TEXT)
    RETURNS INTEGER AS $$
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
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_anonymiser_traces_audit(TEXT, TEXT) IS
    'Remplace une adresse de courriel par le jeton anonyme du compte dans les colonnes '
    'declarees par fn_tables_traces_audit() (F23). Ces colonnes stockent le principal '
    'authentifie, donc l adresse en clair : les vider fait partie de l effacement. '
    'Le remplacement est le jeton anonyme-{id}@supprime.invalid et non NULL : les '
    'colonnes sont nullables, mais une trace d audit doit rester resoluble — savoir '
    'QUE la ligne a ete creee par le compte 42, desormais anonymise, reste une '
    'information de tracabilite ; un NULL la perdrait. Ne touche aucune donnee '
    'comptable : les triggers d immuabilite gardent les montants, numeros et dates, '
    'jamais les colonnes d audit.';
