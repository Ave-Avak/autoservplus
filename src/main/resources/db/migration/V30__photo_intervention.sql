-- =====================================================================================
-- Galerie multi-images : photos d intervention (BL-9).
--
-- 1) CE QUE LE SOCLE PERMETTAIT, ET CE QU IL INTERDISAIT
--
--    La table photo du socle V7 ne rattache une image qu a une prestation (service_id)
--    ou a une piece (piece_id), et son CHECK ck_photo_origine_unique impose un XOR
--    STRICT entre les deux. Ce n est pas un oubli qu on pourrait contourner : la
--    contrainte refuse activement toute ligne dont les deux colonnes seraient nulles,
--    donc toute troisieme origine.
--
--    Les photos avant / apres intervention, elles, ne se rattachent ni a une ligne de
--    catalogue ni a un article de stock : elles documentent un travail precis sur un
--    vehicule precis, a une date donnee. C est exactement le meme raisonnement que
--    pour l avis (BL-4), qui porte sur l intervention et non sur la prestation.
--
-- 2) POURQUOI num_nonnulls PLUTOT QU UNE DISJONCTION ETENDUE
--
--    Le XOR d origine s ecrivait en enumerant les combinaisons admises. A deux
--    colonnes cela tenait en deux clauses ; a trois il en faudrait trois, et la
--    quatrieme origine eventuelle en imposerait quatre — chacune etant une occasion
--    d oublier un cas. num_nonnulls(...) = 1 dit litteralement l invariant voulu,
--    « exactement une origine », et ne changera plus que par l ajout d un argument.
--
--    La contrainte reste indispensable : sans elle, une photo sans aucune origine
--    serait orpheline (invisible partout, jamais nettoyee), et une photo a deux
--    origines s afficherait a deux endroits sans qu on sache lequel fait foi.
--
-- 3) AUCUN BACKFILL
--
--    La table est vide — elle n a jamais ete alimentee depuis le socle V7. Il n y a
--    donc aucune ligne existante a reclasser, et le remplacement du CHECK ne peut
--    invalider aucune donnee.
--
-- 4) ON DELETE CASCADE, COMME LES DEUX AUTRES ORIGINES
--
--    Une photo n a pas de vie propre : elle illustre son porteur. Les FK existantes
--    vers service et piece sont deja en CASCADE ; celle vers intervention l est aussi,
--    par coherence. La suppression du FICHIER sur disque reste a la charge de
--    l application, la base ne connaissant que le chemin.
-- =====================================================================================

ALTER TABLE photo
    ADD COLUMN intervention_id BIGINT;

ALTER TABLE photo
    ADD CONSTRAINT fk_photo_intervention
        FOREIGN KEY (intervention_id) REFERENCES intervention (id)
            ON UPDATE CASCADE ON DELETE CASCADE;

-- Remplacement du XOR a deux colonnes par l invariant « exactement une origine ».
ALTER TABLE photo
    DROP CONSTRAINT ck_photo_origine_unique;

ALTER TABLE photo
    ADD CONSTRAINT ck_photo_origine_unique
        CHECK (num_nonnulls(service_id, piece_id, intervention_id) = 1);

-- Meme patron que les index partiels des deux autres origines : seules les lignes
-- concernees sont indexees, les photos de catalogue n alourdissent pas cet index.
CREATE INDEX ix_photo_intervention ON photo (intervention_id)
    WHERE intervention_id IS NOT NULL;

COMMENT ON COLUMN photo.intervention_id IS
    'Photos avant / apres d une intervention (BL-9). Exclusif avec service_id et piece_id.';
