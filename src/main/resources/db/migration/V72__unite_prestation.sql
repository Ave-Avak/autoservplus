-- =====================================================================================
-- Unité d'une prestation : au forfait ou à l'heure (Q-H-36, groupe H, H4-1b).
--
-- 1) POURQUOI
--
--    La main-d'œuvre se facture à l'heure, au taux du garage. Une prestation à l'heure ne
--    se réserve pas en rendez-vous et ne s'achète pas au panier, le client ne sachant pas
--    combien d'heures il faut : l'atelier l'ajoute en cours d'intervention. Les
--    prestations existantes restent au forfait, valeur par défaut.
--
-- 2) DURÉE D'UNE PRESTATION À L'HEURE
--
--    duree_minutes reste NOT NULL et strictement positive. Pour une prestation à l'heure,
--    elle vaut la durée d'une unité, soixante minutes, et ck_service_duree_heure le
--    garantit. Écarté : la rendre nullable pour ces prestations, ce qui relâcherait une
--    contrainte et obligerait chaque lecteur de la durée, calcul des créneaux compris, à
--    traiter le cas vide.
--
-- 3) UNITÉ FIXÉE À LA CRÉATION
--
--    L'unité ne se modifie plus après la création, comme la plaque d'un véhicule (F10).
--    La garde est dans le domaine (aucune méthode ne la change) : une prestation au
--    forfait déjà dans un panier, un rendez-vous ou un devis ne doit pas devenir une
--    prestation que le domaine refuse.
-- =====================================================================================

ALTER TABLE service
    ADD COLUMN unite VARCHAR(10) NOT NULL DEFAULT 'FORFAIT',
    ADD CONSTRAINT ck_service_unite CHECK (unite IN ('FORFAIT', 'HEURE')),
    ADD CONSTRAINT ck_service_duree_heure CHECK (unite <> 'HEURE' OR duree_minutes = 60);

COMMENT ON COLUMN service.unite IS
    'FORFAIT ou HEURE. Une prestation à l''heure ne se réserve ni ne s''achète en ligne, '
    'sa durée vaut une heure. Fixée à la création.';
