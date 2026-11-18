-- Fenêtre des échecs de connexion (F2, Stratégie A07, contrôle 4).
--
-- Le compteur tentatives_echouees cumulait les échecs jusqu'à la connexion réussie
-- suivante, sans durée : un mot de passe faux par semaine finissait par verrouiller le
-- compte, et un sixième échec juste après la levée d'un verrou le reposait aussitôt.
-- Le seuil vaut désormais sur une fenêtre fixe ouverte par le premier échec : passée la
-- fenêtre, ou levé le verrou, le compteur repart de zéro.
--
-- Une table d'échecs horodatés donnerait une fenêtre glissante, au prix d'une ligne par
-- mot de passe faux à conserver, à purger et à justifier au registre des traitements.
-- Une colonne suffit à « cinq échecs en quinze minutes ».
--
-- Les lignes existantes restent à NULL : l'entité traite une fenêtre absente comme
-- passée, et le prochain échec ouvre une fenêtre neuve.
ALTER TABLE utilisateur ADD COLUMN premier_echec_le TIMESTAMPTZ;

COMMENT ON COLUMN utilisateur.premier_echec_le IS
    'Premier échec de connexion de la fenêtre en cours. NULL si aucune fenêtre n''est ouverte. Remis à NULL par une connexion réussie, un changement de mot de passe et l''anonymisation.';
