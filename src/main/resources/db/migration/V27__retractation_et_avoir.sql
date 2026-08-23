-- =====================================================================================
-- Retractation et note de credit (F30, RM-23) : demande d annulation en deux temps,
-- compteur d avoirs, unicite de l avoir par facture.
--
-- 0) CE QUE CETTE MIGRATION NE FAIT PAS, ET POURQUOI
--
--    Aucune valeur de statut n est ajoutee. Verification faite sur la base reelle :
--    ck_commande_statut admet deja REMBOURSEE, ck_paiement_statut admet deja
--    REMBOURSE (V4), et ck_commande_motif_annulation admet deja RETRACTATION_F30
--    (V24, qui posait d emblee les six motifs du dictionnaire en annoncant que les
--    autres attendraient leur bloc). Le socle avait prevu ce bloc ; il n y a rien a
--    elargir, seulement a exercer. REMBOURSEE reste distinct d ANNULEE : une commande
--    annulee faute de paiement (RM-21) et une commande payee puis remboursee (F30)
--    ne sont pas le meme evenement comptable et ne doivent pas se confondre.
--
-- 1) POURQUOI UNE DEMANDE, ET NON UNE ANNULATION IMMEDIATE
--
--    Le CdC (P372-373, RM-23) veut un flux en deux temps. La raison est verifiable :
--    le controle automatique sait dire si la commande est payee, si elle appartient
--    au membre et si les quatorze jours legaux courent encore ; il ne sait PAS dire
--    si la piece a ete montee sur le vehicule, ni si elle revient dans son emballage
--    d origine. Ce constat est physique, il appartient a l atelier. La demande porte
--    donc la part automatisable de la decision et laisse l autre a l administrateur,
--    qui peut refuser avec motif.
--
--    Une seule demande EN_ATTENTE par commande (index partiel unique) : c est la
--    garde d idempotence, meme esprit que uq_facture_commande. Un double-clic ou un
--    rejeu de formulaire ne cree pas deux dossiers a traiter. Rien n interdit en
--    revanche de redemander apres un refus : le refus porte sur un constat date, il
--    ne prive pas le consommateur de son droit pour toujours. Apres une validation,
--    la commande est REMBOURSEE et la machine a etats refuse d elle-meme toute suite.
--
--    version : la validation est une ressource critique concurrente (double-clic de
--    l administrateur, deux administrateurs sur le meme dossier). Meme patron que
--    rdv, intervention et paiement.
--
--    deleted_at / deleted_by : aucun chemin d ecriture ne les pose en V1 — une
--    demande de retractation est une trace juridique, elle ne se supprime pas. Les
--    colonnes sont la par uniformite avec les entites metier du projet, comme
--    updated_at l est deja sur les tables append-only. Ecart assume et documente.
--
-- 2) POURQUOI UN COMPTEUR D AVOIRS ET NON seq_numero_avoir (V9)
--
--    Exactement le raisonnement de V26, applique au document rectificatif. Une note
--    de credit est soumise a la meme discipline de numerotation que la facture
--    qu elle corrige (AR n°1, art. 5 : suite ininterrompue ; art. 12 pour le
--    document rectificatif). Une sequence PostgreSQL est non transactionnelle par
--    conception : nextval ne se rejoue pas au rollback et creuse un trou des qu une
--    emission echoue. Ce qui disqualifiait la sequence pour la facture la disqualifie
--    pour l avoir — retenir l inverse ferait cohabiter deux raisonnements
--    contradictoires dans le meme module.
--
--    Compteur SEPARE de celui des factures : une note de credit a sa propre suite
--    legale, elle ne consomme pas un numero de facture. Une ligne par exercice, la
--    numerotation repartant a 1 chaque annee civile comme pour la facture.
--
--    seq_numero_avoir devient lettre morte. Elle n est pas supprimee — meme choix
--    que pour seq_numero_facture en V26 — mais son commentaire est reecrit pour
--    qu aucun futur developpeur ne la reprenne en croyant obeir a V9.
--
-- 3) UNE FACTURE, AU PLUS UN AVOIR (perimetre V1)
--
--    L index unique remplace l index simple ix_avoir_facture du socle. Il pose en
--    base la garde d idempotence que le controle applicatif ne peut pas tenir seul :
--    deux validations simultanees passeraient toutes deux la verification, l index
--    refuse la seconde insertion et sa transaction est annulee — donc aussi son
--    remboursement et son numero, rendus au compteur.
--
--    Cette unicite dit AUSSI le perimetre V1 : l annulation est TOTALE. Une
--    annulation partielle par ligne emettrait plusieurs avoirs sur une meme facture
--    et devra lever cet index (V2). Le choix est deliberement inscrit dans le schema
--    plutot que laisse a une convention de code.
--
--    L avoir ne porte pas de commande_id : il pointe la facture, et uq_facture_commande
--    (V26) garantit deja une facture au plus par commande. Ajouter la colonne
--    dupliquerait un chemin qui existe, avec le risque de divergence qui va avec.
--
-- 4) paiement.reference_remboursement : contrepartie de reference_mollie pour le
--    Refund. La cle d idempotence du remboursement est derivee de la reference du
--    paiement cote applicatif, elle n a pas besoin de colonne ; l identifiant rendu
--    par le prestataire, lui, est le seul point de rapprochement avec son extrait —
--    sans lui, un remboursement conteste ne se retrouve pas.
-- =====================================================================================

CREATE TABLE demande_annulation (
    id             BIGSERIAL     PRIMARY KEY,
    reference      UUID          NOT NULL DEFAULT gen_random_uuid(),
    commande_id    BIGINT        NOT NULL,
    statut         VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE',
    motif_membre   TEXT,
    motif_decision TEXT,
    decide_par     BIGINT,
    decide_le      TIMESTAMPTZ,
    avoir_id       BIGINT,
    date_demande   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     VARCHAR(120),
    updated_by     VARCHAR(120),
    deleted_at     TIMESTAMPTZ,
    deleted_by     VARCHAR(120),
    CONSTRAINT uq_demande_annulation_reference UNIQUE (reference),
    CONSTRAINT fk_demande_annulation_commande FOREIGN KEY (commande_id)
        REFERENCES commande (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_demande_annulation_decideur FOREIGN KEY (decide_par)
        REFERENCES utilisateur (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_demande_annulation_avoir FOREIGN KEY (avoir_id)
        REFERENCES avoir (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_demande_annulation_statut
        CHECK (statut IN ('EN_ATTENTE', 'VALIDEE', 'REFUSEE')),
    -- Une demande non tranchee ne porte aucune trace de decision ; une demande
    -- tranchee porte toujours son auteur et sa date. L etat « validee par personne »
    -- devient inexprimable : il n a plus a etre surveille par le code.
    CONSTRAINT ck_demande_annulation_decision CHECK (
        (statut =  'EN_ATTENTE' AND decide_par IS NULL     AND decide_le IS NULL)
     OR (statut <> 'EN_ATTENTE' AND decide_par IS NOT NULL AND decide_le IS NOT NULL)),
    -- L avoir est le produit exclusif d une validation : jamais sur une demande en
    -- attente ou refusee, toujours sur une demande validee.
    CONSTRAINT ck_demande_annulation_avoir CHECK (
        (statut =  'VALIDEE' AND avoir_id IS NOT NULL)
     OR (statut <> 'VALIDEE' AND avoir_id IS NULL))
);

COMMENT ON TABLE demande_annulation IS
    'Demande de retractation d une commande de marchandises (F30, RM-23), tranchee par '
    'l administrateur. Le controle automatique porte ce que le systeme sait (proprietaire, '
    'commande payee, delai de 14 jours) ; l etat physique de la piece releve de l atelier, '
    'd ou la validation humaine. Perimetre V1 : annulation totale de la commande.';
COMMENT ON COLUMN demande_annulation.motif_membre IS
    'Facultatif : le droit de retractation est inconditionnel, le consommateur n a pas '
    'a se justifier (CDE, art. VI.47). Recueilli a titre d information pour le garage.';
COMMENT ON COLUMN demande_annulation.motif_decision IS
    'Renseigne surtout au refus : le consommateur doit savoir sur quel constat le garage '
    's est appuye pour lui opposer une exception.';
COMMENT ON COLUMN demande_annulation.deleted_at IS
    'Inerte en V1 : une demande de retractation est une trace juridique, aucun chemin '
    'ne la supprime. Colonne presente par uniformite avec les entites metier du projet.';

-- Une seule demande en attente par commande : garde d idempotence contre le
-- double-clic et le rejeu de formulaire. Ne contraint que EN_ATTENTE — redemander
-- apres un refus reste possible, un refus portant sur un constat date.
CREATE UNIQUE INDEX uq_demande_annulation_en_attente
    ON demande_annulation (commande_id)
    WHERE statut = 'EN_ATTENTE' AND deleted_at IS NULL;

CREATE INDEX ix_demande_annulation_commande
    ON demande_annulation (commande_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_demande_annulation_statut
    ON demande_annulation (statut) WHERE deleted_at IS NULL;

-- --- numerotation des avoirs ---------------------------------------------------------

CREATE TABLE compteur_avoir (
    exercice       SMALLINT    PRIMARY KEY,
    dernier_numero INTEGER     NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     VARCHAR(120),
    CONSTRAINT ck_compteur_avoir_numero CHECK (dernier_numero >= 0)
);

COMMENT ON TABLE compteur_avoir IS
    'Compteur transactionnel des notes de credit, une ligne par exercice comptable. '
    'Distinct de compteur_facture : un avoir a sa propre suite legale et ne consomme pas '
    'un numero de facture. Incremente sous SELECT ... FOR UPDATE dans la transaction '
    'd emission, pour la meme raison qu en V26 : un rollback annule l increment et la '
    'suite reste sans trou. Ne jamais remplacer par une sequence.';

COMMENT ON SEQUENCE seq_numero_avoir IS
    'INUTILISEE depuis V27. Conservee pour ne pas reecrire l historique des migrations. '
    'Une sequence n est pas transactionnelle : elle laisse des trous au rollback, ce qui '
    'est interdit pour un document rectificatif au meme titre que pour une facture. '
    'Voir la table compteur_avoir.';

-- --- unicite de l avoir par facture ---------------------------------------------------

-- L index simple du socle est remplace : l unique le couvre pour la recherche et
-- ajoute la garde. Conserver les deux entretiendrait un index redondant.
DROP INDEX ix_avoir_facture;
CREATE UNIQUE INDEX uq_avoir_facture ON avoir (facture_id);

COMMENT ON INDEX uq_avoir_facture IS
    'Une facture, au plus un avoir (perimetre V1 : annulation totale). Garde '
    'd idempotence contre deux validations simultanees, et enonce du perimetre : '
    'l annulation partielle par ligne (V2) devra lever cette unicite.';

-- --- immuabilite de la note de credit -------------------------------------------------

-- Le socle protegeait la facture par tg_facture_immuable mais laissait l avoir nu :
-- l oubli se comprend, aucun code n emettait encore d avoir. Une note de credit est
-- pourtant un document comptable au meme titre que la facture, et sa correction ne
-- passe pas davantage par un UPDATE. Le seul champ mutable est chemin_pdf, pose apres
-- coup a la premiere generation du document — il ne porte aucune donnee comptable,
-- exactement comme sur la facture.
CREATE OR REPLACE FUNCTION fn_avoir_immuable() RETURNS TRIGGER AS $$
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
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_avoir_immuable
    BEFORE UPDATE ON avoir
    FOR EACH ROW EXECUTE FUNCTION fn_avoir_immuable();

-- --- rapprochement du remboursement ---------------------------------------------------

ALTER TABLE paiement ADD COLUMN reference_remboursement VARCHAR(64);

COMMENT ON COLUMN paiement.reference_remboursement IS
    'Identifiant du Refund chez le prestataire, contrepartie de reference_mollie. '
    'Seul point de rapprochement avec l extrait du prestataire en cas de contestation. '
    'La cle d idempotence du remboursement, elle, est derivee de paiement.reference et '
    'n a pas besoin d etre stockee.';

CREATE UNIQUE INDEX uq_paiement_remboursement ON paiement (reference_remboursement)
    WHERE reference_remboursement IS NOT NULL;
