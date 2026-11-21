-- =====================================================================================
-- Recherche du catalogue insensible aux accents (F5).
--
-- « embrayage » saisi sans accent doit trouver « Kit d'embrayage renforcé », et
-- « revision » la prestation « Révision ». unaccent() retire les signes diacritiques des
-- deux côtés de la comparaison, dans la requête de recherche.
--
-- L'extension fait partie de la distribution de PostgreSQL et elle est déclarée « de
-- confiance » depuis la version 13 : le propriétaire de la base l'installe sans droit de
-- super-utilisateur, comme pgcrypto (V1) et btree_gist (V13).
--
-- Aucun index : le catalogue d'un garage compte quelques centaines de lignes, et un
-- motif encadré de jokers ne se sert pas d'un index B-tree.
-- =====================================================================================

CREATE EXTENSION IF NOT EXISTS unaccent;
