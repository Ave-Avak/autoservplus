-- Prestations montrées à l'accueil, dans l'ordre voulu par le garage.
--
-- L'accueil montrait la première prestation de chaque catégorie, une règle qui ne dit
-- rien de ce que les clients demandent le plus (vidange, révision, plaquettes). Le garage
-- choisit : un rang à partir de 1, nul pour une prestation absente de l'accueil. Le choix
-- au back-office vient avec I4, la graine de démonstration pose les rangs d'ici là.

ALTER TABLE service ADD COLUMN ordre_vitrine SMALLINT;

ALTER TABLE service ADD CONSTRAINT ck_service_ordre_vitrine
    CHECK (ordre_vitrine IS NULL OR ordre_vitrine > 0);

COMMENT ON COLUMN service.ordre_vitrine IS
    'Rang de la prestation dans les tarifs de l''accueil, à partir de 1. Nul : absente de l''accueil.';
