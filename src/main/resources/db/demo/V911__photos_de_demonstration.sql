-- Photos du catalogue de démonstration : une par prestation, un visuel par catégorie de
-- produits, une photo propre pour cinq articles mis en avant.
--
-- Photos de la licence Unsplash (jamais Unsplash+), regardées une à une : ni visage, ni
-- plaque lisible, ni logo de marque mis en avant. Auteurs et adresses dans
-- docs/credits-photos.md. Les fichiers voyagent dans le classpath (demonstration/photos)
-- et PhotosDemonstration les dépose sous la racine des médias au démarrage sous le profil
-- demo : une migration n'écrit pas de fichier.
--
-- Chaque photo existe en 1024 x 768 et en vignette de 480 x 360, recadrées en 4:3 au
-- téléchargement. Une ligne déjà présente pour le même fichier n'est pas recréée.

-- Prestations, par leur code.
INSERT INTO photo (service_id, chemin, chemin_vignette, texte_alt, texte_alt_nl, texte_alt_en, ordre,
                   largeur, hauteur, created_by, updated_by)
SELECT t.id, 'demonstration/' || v.fichier || '.webp', 'demonstration/' || v.fichier || '-480.webp',
       v.fr, v.nl, v.en, 0, 1024, 768, 'demo', 'demo'
  FROM (VALUES
          ('VIDANGE_STD', 'vidange-std', 'Huile moteur qui s''écoule d''un bec métallique', 'Motorolie die uit een metalen tuit stroomt', 'Engine oil flowing from a metal spout'),
          ('REVISION_ANNUELLE', 'revision-annuelle', 'Mécanicien penché sur le compartiment moteur d''une voiture rouge', 'Monteur gebogen over de motorruimte van een rode auto', 'Mechanic leaning over the engine bay of a red car'),
          ('REVISION_COMPLETE', 'revision-complete', 'Mains qui serrent un écrou avec une clé dans un moteur', 'Handen die met een sleutel een moer in een motor aandraaien', 'Hands tightening a nut with a spanner inside an engine'),
          ('FILTRE_AIR', 'filtre-air', 'Filtre à air conique dans un compartiment moteur', 'Kegelvormige luchtfilter in een motorruimte', 'Cone air filter in an engine bay'),
          ('BOUGIES', 'bougies', 'Bougie d''allumage neuve sur fond gris', 'Nieuwe bougie op een grijze achtergrond', 'New spark plug on a grey background'),
          ('FILTRE_HABITACLE', 'filtre-habitacle', 'Grille d''aération chromée du tableau de bord', 'Verchroomd ventilatierooster in het dashboard', 'Chrome air vent in the dashboard'),
          ('DESINFECTION_CLIM', 'desinfection-clim', 'Aérateurs de ventilation sur la console centrale', 'Ventilatieroosters op de middenconsole', 'Air vents on the centre console'),
          ('PLAQUETTES_AV', 'plaquettes-av', 'Étrier et disque de frein d''une roue démontée', 'Remklauw en remschijf van een gedemonteerd wiel', 'Brake calliper and disc with the wheel removed'),
          ('PLAQUETTES_AR', 'plaquettes-ar', 'Disque de frein et étrier vus de face', 'Remschijf en remklauw van voren gezien', 'Brake disc and calliper seen from the front'),
          ('DISQUES_PLAQUETTES_AV', 'disques-plaquettes-av', 'Disque de frein éclairé sous une voiture levée', 'Verlichte remschijf onder een opgetilde auto', 'Lit brake disc under a raised car'),
          ('DISQUES_PLAQUETTES_AR', 'disques-plaquettes-ar', 'Disque percé et étrier blanc sur une voiture en atelier', 'Geperforeerde schijf en witte remklauw op een auto in de werkplaats', 'Drilled disc and white calliper on a car in the workshop'),
          ('CONTROLE_FREINAGE', 'controle-freinage', 'Disque de frein et suspension d''une voiture en atelier', 'Remschijf en ophanging van een auto in de werkplaats', 'Brake disc and suspension of a car in the workshop'),
          ('MONTAGE_PNEU', 'montage-pneu', 'Pneu posé sur une jante à la machine', 'Band die met de machine op een velg wordt gelegd', 'Tyre being fitted on a rim by machine'),
          ('PERMUTATION_PNEUS', 'permutation-pneus', 'Mains gantées qui tiennent une roue sur une voiture', 'Gehandschoende handen die een wiel aan een auto vasthouden', 'Gloved hands holding a wheel on a car'),
          ('CHANGEMENT_ROUES', 'changement-roues', 'Pneus empilés devant un atelier', 'Gestapelde banden voor een werkplaats', 'Tyres stacked in front of a workshop'),
          ('GEOMETRIE', 'geometrie', 'Atelier équipé de ponts élévateurs rouges', 'Werkplaats met rode hefbruggen', 'Workshop with red vehicle lifts'),
          ('CREVAISON', 'crevaison', 'Flanc d''un pneu monté sur une voiture', 'Zijwand van een band op een auto', 'Sidewall of a tyre on a car'),
          ('LIQUIDE_REFROIDISSEMENT', 'liquide-refroidissement', 'Liquide versé dans un réservoir du moteur', 'Vloeistof die in een reservoir van de motor wordt gegoten', 'Fluid being poured into an engine reservoir'),
          ('DIAG_ELEC', 'diag-elec', 'Mains sur une tablette de diagnostic', 'Handen op een diagnosetablet', 'Hands on a diagnostic tablet'),
          ('CONTROLE_BATTERIE', 'controle-batterie', 'Pinces de câbles de démarrage sur une batterie', 'Klemmen van startkabels op een accu', 'Jump lead clamps on a battery'),
          ('REMPLACEMENT_BATTERIE', 'remplacement-batterie', 'Batterie dans le compartiment moteur', 'Accu in de motorruimte', 'Battery in the engine bay'),
          ('AMPOULES_PHARES', 'ampoules-phares', 'Phare allumé d''une voiture sombre', 'Brandende koplamp van een donkere auto', 'Lit headlight of a dark car'),
          ('BALAIS_ESSUIE_GLACE', 'balais-essuie-glace', 'Balai d''essuie-glace sur un pare-brise', 'Ruitenwisser op een voorruit', 'Wiper blade on a windscreen'),
          ('KIT_DISTRIBUTION', 'kit-distribution', 'Courroie et galets à l''avant d''un moteur', 'Riem en rollen aan de voorkant van een motor', 'Belt and pulleys at the front of an engine'),
          ('EMBRAYAGE', 'embrayage', 'Pommeau d''un levier de vitesses manuel', 'Pookknop van een handgeschakelde versnellingsbak', 'Knob of a manual gear lever'),
          ('RECHARGE_CLIM', 'recharge-clim', 'Commandes de climatisation et d''éclairage au tableau de bord', 'Bediening van airco en verlichting op het dashboard', 'Climate and lighting controls on the dashboard'),
          ('RETOUCHE_PEINTURE', 'retouche-peinture', 'Main gantée qui retouche la peinture d''une aile orange', 'Gehandschoende hand die de lak van een oranje spatbord bijwerkt', 'Gloved hand touching up the paint of an orange wing'),
          ('RENOVATION_OPTIQUES', 'renovation-optiques', 'Phare d''une voiture noire en gros plan', 'Koplamp van een zwarte auto in close-up', 'Close-up of a black car''s headlight'),
          ('PURGE_FREIN', 'purge-frein', 'Étrier de frein blanc et disque percé, vus de côté', 'Witte remklauw en geperforeerde schijf, van opzij gezien', 'White brake calliper and drilled disc seen from the side'),
          ('PREPARATION_CT', 'preparation-ct', 'Main qui coche une liste de contrôle', 'Hand die een checklist afvinkt', 'Hand ticking a checklist')
       ) AS v(cle, fichier, fr, nl, en)
  JOIN service t ON t.code = v.cle
 WHERE NOT EXISTS (SELECT 1 FROM photo p WHERE p.chemin = 'demonstration/' || v.fichier || '.webp');

-- Visuels de catégorie (V79), montrés sur la fiche d'un article sans photo propre.
INSERT INTO photo (categorie_id, chemin, chemin_vignette, texte_alt, texte_alt_nl, texte_alt_en, ordre,
                   largeur, hauteur, created_by, updated_by)
SELECT t.id, 'demonstration/' || v.fichier || '.webp', 'demonstration/' || v.fichier || '-480.webp',
       v.fr, v.nl, v.en, 0, 1024, 768, 'demo', 'demo'
  FROM (VALUES
          ('P_FILTRES', 'categorie-p-filtres', 'Main gantée qui retire un filtre à huile usagé', 'Gehandschoende hand die een gebruikte oliefilter verwijdert', 'Gloved hand removing a used oil filter'),
          ('P_MOTEUR', 'categorie-p-moteur', 'Arbres à cames et chaîne de distribution d''un moteur ouvert', 'Nokkenassen en distributieketting van een geopende motor', 'Camshafts and timing chain of an opened engine'),
          ('P_PNEUS', 'categorie-p-pneus', 'Pneus empilés vus de près', 'Gestapelde banden van dichtbij', 'Close-up of stacked tyres'),
          ('P_BATTERIE', 'categorie-p-batterie', 'Bouton de démarrage d''une voiture', 'Startknop van een auto', 'Car start button'),
          ('P_ECLAIRAGE', 'categorie-p-eclairage', 'Phare et antibrouillard allumés de nuit', 'Koplamp en mistlamp die ''s nachts branden', 'Headlight and fog light on at night'),
          ('E_NIVEAUX', 'categorie-e-niveaux', 'Deux bidons de liquide posés au sol', 'Twee bussen vloeistof op de grond', 'Two fluid containers on the ground'),
          ('P_ECHAPPEMENT', 'categorie-p-echappement', 'Double sortie d''échappement', 'Dubbele uitlaat', 'Twin exhaust tips'),
          ('P_SUSPENSION', 'categorie-p-suspension', 'Quatre amortisseurs à ressorts rouges', 'Vier schokdempers met rode veren', 'Four shock absorbers with red springs'),
          ('E_EXTERIEUR', 'categorie-e-exterieur', 'Gant de lavage sur une carrosserie savonnée', 'Washandschoen op een ingezeepte carrosserie', 'Wash mitt on a soapy car body'),
          ('E_INTERIEUR', 'categorie-e-interieur', 'Main gantée qui tient un pulvérisateur dans l''habitacle', 'Gehandschoende hand met een sproeier in het interieur', 'Gloved hand holding a spray bottle inside the car'),
          ('A_HIVER', 'categorie-a-hiver', 'Phare d''une voiture sous la neige', 'Koplamp van een auto in de sneeuw', 'Headlight of a car in the snow'),
          ('A_SECURITE', 'categorie-a-securite', 'Triangle de signalisation au bord de la route', 'Gevarendriehoek langs de weg', 'Warning triangle by the roadside'),
          ('A_EXTERIEUR', 'categorie-a-exterieur', 'Voiture ancienne avec porte-bagages de toit', 'Oude auto met imperiaal', 'Classic car with a roof rack'),
          ('P_FREINAGE', 'categorie-p-freinage', 'Étrier de frein rouge derrière une jante', 'Rode remklauw achter een velg', 'Red brake calliper behind a rim'),
          ('A_INTERIEUR', 'categorie-a-interieur', 'Siège en cuir noir', 'Zwarte lederen zetel', 'Black leather seat')
       ) AS v(cle, fichier, fr, nl, en)
  JOIN categorie t ON t.code = v.cle
 WHERE NOT EXISTS (SELECT 1 FROM photo p WHERE p.chemin = 'demonstration/' || v.fichier || '.webp');

-- Articles mis en avant, par leur référence fabricant.
INSERT INTO photo (piece_id, chemin, chemin_vignette, texte_alt, texte_alt_nl, texte_alt_en, ordre,
                   largeur, hauteur, created_by, updated_by)
SELECT t.id, 'demonstration/' || v.fichier || '.webp', 'demonstration/' || v.fichier || '-480.webp',
       v.fr, v.nl, v.en, 0, 1024, 768, 'demo', 'demo'
  FROM (VALUES
          ('PN-2055516', 'article-pneu-4s', 'Sculptures d''un pneu en gros plan', 'Profiel van een band in close-up', 'Close-up of a tyre tread'),
          ('BB-A011', 'article-coffre-toit', 'Coffre de toit noir sur un break', 'Zwarte dakkoffer op een stationwagen', 'Black roof box on an estate car'),
          ('BB-E005', 'article-seau-lavage', 'Seau de lavage bleu', 'Blauwe wasemmer', 'Blue wash bucket'),
          ('BB-A010', 'article-barres-toit', 'Barres de toit sur une voiture ancienne', 'Dakdragers op een oude auto', 'Roof bars on an old car'),
          ('BB-A001', 'article-tapis-sol', 'Pédalier au-dessus du tapis de sol', 'Pedalen boven de vloermat', 'Pedals above the floor mat')
       ) AS v(cle, fichier, fr, nl, en)
  JOIN piece t ON t.reference_fabricant = v.cle
 WHERE NOT EXISTS (SELECT 1 FROM photo p WHERE p.chemin = 'demonstration/' || v.fichier || '.webp');
