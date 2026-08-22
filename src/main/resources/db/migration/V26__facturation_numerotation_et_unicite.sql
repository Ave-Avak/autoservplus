-- =====================================================================================
-- Facturation (F31) : compteur de numerotation CONTINUE, unicite de la source, et
-- alignement du taux de TVA sur les factures multi-taux.
--
-- 1) POURQUOI UNE TABLE COMPTEUR ET NON LA SEQUENCE seq_numero_facture (V9)
--
--    Une facture belge doit porter un numero sequentiel SANS TROU (AR n°1, art. 5 :
--    numerotation ininterrompue). Une sequence PostgreSQL est deliberement NON
--    transactionnelle : nextval() ne se rejoue pas au rollback, precisement pour ne
--    pas serialiser les inserts. Consequence : toute transaction annulee apres un
--    nextval() emporte definitivement son numero et creuse un trou dans la suite.
--    C'est le comportement voulu pour un id technique (commande, intervention) ;
--    c'est disqualifiant pour un numero de facture.
--
--    Le compteur vit donc dans une TABLE ordinaire, incrementee sous verrou de ligne
--    (SELECT ... FOR UPDATE) dans la transaction meme qui insere la facture. Les deux
--    ecritures partagent alors le meme sort : si l'emission echoue, l'increment est
--    annule avec elle et le numero reste disponible pour la facture suivante. Le prix
--    a payer est la serialisation des emissions concurrentes — pour quelques factures
--    par jour, c'est sans objet, et c'est le seul moyen d'obtenir la garantie legale.
--
--    seq_numero_facture (V9) devient donc lettre morte. Elle n'est PAS supprimee :
--    supprimer un objet cree par une migration deja jouee reste possible mais inutile
--    ici, et son commentaire est reecrit pour qu'aucun futur developpeur ne la
--    reprenne par erreur en croyant obeir a V9.
--
--    Un compteur PAR EXERCICE : la numerotation legale repart a 1 chaque annee civile
--    (le numero porte l'annee, la suite ne peut donc pas se confondre d'un exercice a
--    l'autre). L'exercice est la cle primaire — une ligne par annee, creee a la
--    premiere facture de l'annee.
--
-- 2) UNICITE DE LA SOURCE : une commande (ou une intervention) donne AU PLUS une
--    facture. L'evenement CommandePayeeEvent peut etre rejoue ; le service verifie
--    l'existence prealable, mais une verification applicative ne survit pas a une
--    course entre deux rejeux simultanes. Les index partiels tranchent en base.
--    L'index sur intervention_id est pose des maintenant bien que la facturation
--    d'intervention (RM-17) soit un bloc futur : c'est la meme regle metier, et
--    l'ecrire ici evite une migration supplementaire pour la re-enoncer.
--
-- 3) taux_tva_applique DEVIENT NULLABLE. V6 le posait NOT NULL, ce qui suppose un
--    taux unique par facture. Une commande peut melanger 6 % et 21 % (piece de
--    rechange contre prestation a taux reduit) : aucun taux unique n'est alors vrai.
--    Semantique retenue : le taux quand il est unique, NULL quand la facture est
--    multi-taux — le PDF porte de toute facon la ventilation par taux, calculee des
--    lignes. Mieux vaut une colonne vide qu'une colonne fausse sur un document
--    declare a la TVA. Le trigger d'immuabilite continue de la proteger : elle est
--    figee des l'emission, nullable ou non.
-- =====================================================================================

CREATE TABLE compteur_facture (
    exercice       SMALLINT    PRIMARY KEY,
    dernier_numero INTEGER     NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     VARCHAR(120),
    CONSTRAINT ck_compteur_facture_numero CHECK (dernier_numero >= 0)
);

COMMENT ON TABLE compteur_facture IS
    'Compteur transactionnel des factures, une ligne par exercice comptable. '
    'Incremente sous SELECT ... FOR UPDATE dans la transaction d emission : un rollback '
    'annule l increment, ce qui garantit une numerotation sans trou (obligation legale). '
    'Ne jamais remplacer par une sequence, qui laisserait des trous par construction.';
COMMENT ON COLUMN compteur_facture.dernier_numero IS
    'Dernier numero attribue pour cet exercice. La prochaine facture porte dernier_numero + 1.';

COMMENT ON SEQUENCE seq_numero_facture IS
    'INUTILISEE depuis V26. Conservee pour ne pas reecrire l historique des migrations. '
    'Une sequence n est pas transactionnelle : elle laisse des trous au rollback, ce qui '
    'est interdit pour un numero de facture. Voir la table compteur_facture.';

-- Une source, au plus une facture. Index partiels : la colonne opposee est NULL sur
-- chaque facture (CHECK ck_facture_origine_unique), les NULL ne doivent pas s exclure
-- entre eux.
CREATE UNIQUE INDEX uq_facture_commande ON facture (commande_id)
    WHERE commande_id IS NOT NULL;
CREATE UNIQUE INDEX uq_facture_intervention ON facture (intervention_id)
    WHERE intervention_id IS NOT NULL;

ALTER TABLE facture ALTER COLUMN taux_tva_applique DROP NOT NULL;
COMMENT ON COLUMN facture.taux_tva_applique IS
    'Taux unique de la facture, ou NULL si elle melange plusieurs taux : la ventilation '
    'par taux est alors portee par le document, calculee des lignes de la source.';
