-- Donnees de demonstration : postes de l atelier pour rendre la reservation
-- utilisable. Sans poste actif, DisponibiliteService.creneauxDuJour renvoie
-- systematiquement une liste vide (la capacite = nombre de postes actifs).
--
-- V13 a bascule le modele de creneau stocke vers une planification par postes
-- mais n a pas pousse de seed correspondant : cette V17 comble le vide.
--
-- Trois postes suffisent pour la demo et permettent d exercer la concurrence
-- (deux membres reservent le meme creneau, deux places restent). A calibrer
-- sur l atelier reel avant mise en production.
--
-- reference UUID et actif = TRUE sont poses par les valeurs par defaut du DDL
-- (V13). Seuls libelle, description et ordre sont renseignes ici.

INSERT INTO poste_atelier (libelle, description, ordre) VALUES
    ('Pont 1',          'Pont elevateur principal',                          1),
    ('Pont 2',          'Pont elevateur secondaire',                         2),
    ('Baie diagnostic', 'Baie sans elevation pour diagnostic et pneumatique', 3);
