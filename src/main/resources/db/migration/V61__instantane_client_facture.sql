-- =====================================================================================
-- Instantané du client sur la facture, figé à l'émission (AR n°1, art. 5).
--
-- 1) POURQUOI
--
--    Le PDF d'une facture se fabrique à la première demande, depuis l'entité vive du
--    membre. Une adresse modifiée aujourd'hui ressortait sur une facture émise il y a des
--    mois, et une facture jamais téléchargée avant la suppression du compte sortait avec
--    « Client supprimé » et sans adresse, donc sans ses mentions obligatoires. Le nom,
--    l'adresse et la langue du document sont désormais recopiés sur la facture à son
--    émission, comme RM-30 fige déjà prix, libellés et taux sur les lignes.
--
--    L'adresse de courriel n'est pas recopiée et ne figure plus sur le document : ce n'est
--    pas une mention obligatoire, et la conserver dix ans serait garder une donnée de plus.
--
-- 2) FACTURES DÉJÀ ÉMISES
--
--    Elles reçoivent les valeurs de l'entité vive au moment de la migration. Un compte
--    déjà anonymisé y laisse son marqueur (« Client », « supprimé »), que le document
--    traduit. Écart assumé : un PDF déjà archivé, servi tel quel et qui fait foi, peut
--    porter une adresse antérieure à la valeur recopiée. Seules des factures de
--    démonstration existent, aucune facture réelle n'a été émise.
--
-- 3) RGPD
--
--    L'instantané survit à l'anonymisation (art. 17.3.b) : la facture est conservée dix
--    ans au titre de l'article 60 du Code de la TVA, et doit porter l'identité du client.
--    Le commentaire de V28 qui disait facture et avoir « sans aucun nom » ne vaut plus
--    depuis cette migration. V28 n'est pas modifiée, le commentaire de table ci-dessous le
--    corrige.
--
-- 4) AUCUN COMPLÉMENT EN BASE
--
--    Le schéma ne complète jamais un instantané manquant : le NOT NULL doit faire
--    échouer une émission qui l'oublierait, au lieu de recopier le compte vif en
--    silence. Seule la graine de démonstration V900, antérieure à V61, en a besoin : son
--    complément vit dans db/demo (V899), chargé sous le seul profil demo.
--
-- 5) IMMUABILITÉ
--
--    fn_facture_immuable (V6) est réécrite pour couvrir les nouvelles colonnes, après la
--    reprise des factures existantes, que l'ancienne version laissait passer.
-- =====================================================================================

ALTER TABLE facture
    ADD COLUMN client_prenom      VARCHAR(80),
    ADD COLUMN client_nom         VARCHAR(80),
    ADD COLUMN client_rue         VARCHAR(150),
    ADD COLUMN client_numero_rue  VARCHAR(15),
    ADD COLUMN client_code_postal VARCHAR(10),
    ADD COLUMN client_localite    VARCHAR(100),
    ADD COLUMN client_pays        VARCHAR(60),
    ADD COLUMN langue_document    VARCHAR(2);

UPDATE facture f
SET client_prenom      = u.prenom,
    client_nom         = u.nom,
    client_rue         = u.rue,
    client_numero_rue  = u.numero_rue,
    client_code_postal = u.code_postal,
    client_localite    = u.localite,
    client_pays        = u.pays,
    langue_document    = u.langue
FROM utilisateur u
WHERE u.id = f.membre_id;

ALTER TABLE facture
    ALTER COLUMN client_prenom SET NOT NULL,
    ALTER COLUMN client_nom SET NOT NULL,
    ALTER COLUMN client_pays SET NOT NULL,
    ALTER COLUMN langue_document SET NOT NULL;

ALTER TABLE facture ADD CONSTRAINT ck_facture_langue_document
    CHECK (langue_document IN ('fr', 'nl', 'en'));

CREATE OR REPLACE FUNCTION fn_facture_immuable() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.numero                IS DISTINCT FROM NEW.numero
       OR OLD.montant_htva       IS DISTINCT FROM NEW.montant_htva
       OR OLD.montant_tva        IS DISTINCT FROM NEW.montant_tva
       OR OLD.montant_tvac       IS DISTINCT FROM NEW.montant_tvac
       OR OLD.taux_tva_applique  IS DISTINCT FROM NEW.taux_tva_applique
       OR OLD.date_emission      IS DISTINCT FROM NEW.date_emission
       OR OLD.client_prenom      IS DISTINCT FROM NEW.client_prenom
       OR OLD.client_nom         IS DISTINCT FROM NEW.client_nom
       OR OLD.client_rue         IS DISTINCT FROM NEW.client_rue
       OR OLD.client_numero_rue  IS DISTINCT FROM NEW.client_numero_rue
       OR OLD.client_code_postal IS DISTINCT FROM NEW.client_code_postal
       OR OLD.client_localite    IS DISTINCT FROM NEW.client_localite
       OR OLD.client_pays        IS DISTINCT FROM NEW.client_pays
       OR OLD.langue_document    IS DISTINCT FROM NEW.langue_document THEN
        RAISE EXCEPTION 'Une facture émise est immuable. Émettez une note de crédit.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON TABLE facture IS
    'Facture émise. Porte depuis V61 l''instantané du client (nom, adresse, langue du '
    'document) figé à l''émission, conservé à l''anonymisation du compte (RGPD art. 17.3.b, '
    'Code TVA art. 60).';
COMMENT ON COLUMN facture.langue_document IS
    'Langue du document, celle du compte à l''émission : la facture ne change plus de '
    'langue quand le membre change la sienne.';
