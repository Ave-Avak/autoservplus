-- =====================================================================================
-- Langue des documents d'une commande et d'une intervention.
--
-- En Belgique, la langue d'une facture suit la région du siège d'exploitation de
-- l'émetteur, et non celle du client : néerlandais en Flandre, français en Wallonie,
-- français ou néerlandais à Bruxelles (lois coordonnées du 18 juillet 1966 sur l'emploi
-- des langues, art. 52 § 1er, décret flamand du 19 juillet 1973, art. 2, décret de la
-- Communauté française du 30 juin 1982). L'anglais n'est admis nulle part.
--
-- Les lignes d'une facture doivent être dans la langue de la facture. La langue se
-- décide donc une fois, quand la commande est conclue : c'est à ce moment que les
-- libellés de ses lignes sont figés, dans cette langue, et la facture puis l'avoir la
-- reprennent. La colonne vit sur la commande parce que c'est elle qui existe à ce
-- moment : la facture ne naît qu'au paiement.
--
-- Une intervention suit la même règle : sa langue est fixée à sa création, reprise de la
-- commande quand elle en naît, et ses lignes tirées du catalogue s'y figent.
--
-- Les commandes et les interventions existantes reçoivent « fr » : leurs lignes ont été
-- figées en français, seule langue du catalogue avant V65. La valeur par défaut reste
-- en place pour la graine de démonstration, écrite avant cette colonne et intouchable.
-- Le code fournit toujours la valeur.
--
-- ck_facture_langue_document (V61) n'est pas resserrée : des factures déjà émises
-- peuvent porter « en », et une facture émise ne se réécrit pas.
-- =====================================================================================

ALTER TABLE commande
    ADD COLUMN langue_document VARCHAR(2) NOT NULL DEFAULT 'fr';

ALTER TABLE commande
    ADD CONSTRAINT ck_commande_langue_document CHECK (langue_document IN ('fr', 'nl'));

ALTER TABLE intervention
    ADD COLUMN langue_document VARCHAR(2) NOT NULL DEFAULT 'fr';

ALTER TABLE intervention
    ADD CONSTRAINT ck_intervention_langue_document CHECK (langue_document IN ('fr', 'nl'));

COMMENT ON COLUMN commande.langue_document IS
    'Langue de la facture et de l''avoir de cette commande, et des libellés figés sur ses lignes. Fixée à la conclusion de la commande : langue du membre si le siège l''admet, sinon langue par défaut du siège.';
COMMENT ON COLUMN intervention.langue_document IS
    'Langue des libellés figés sur les lignes de l''intervention. Fixée à sa création, reprise de la commande quand l''intervention en naît.';
