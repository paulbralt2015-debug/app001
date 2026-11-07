# Twins Video Player 2 : version Android TV

Application Android TV (Kotlin, ExoPlayer / Media3, OpenGL ES 2.0). Conçue pour les TV Android 5 à 8,
dont les Coocaa. Elle reprend les effets du lecteur de bureau.

## Fonctions
- Lecture de fichiers (liste des vidéos de la TV, clé USB via « Ouvrir des fichiers », ouverture depuis
  un autre appli), liste de lecture, reprise de lecture, piste audio suivante.
- Effets image en temps réel sur le GPU (shaders) : rendus VHS, Betamax, LaserDisc, QuickTime Windows 95,
  rendus PAL / SECAM / NTSC, sparklies blancs / noirs / couleur, mauvaise réception, destruction des couleurs
  PAL, destruction totale (Nagravision Syster, VideoCrypt), simulation de cryptage Nagravision (Syster),
  VideoCrypt, Eurocrypt, VideoCipher II, lignes de balayage.
- Audio : Dolby Surround, Pro Logic, Pro Logic II (approximation matricielle), stéréo élargie, sortie casque
  ou 5.1, retard surround, basses, mode nuit, cryptage du son (inversion de spectre).
- Réglages : vitesse, format d'image, définition d'affichage, qualité des effets, effets actifs / inactifs.

## Télécommande
| Touche | Action |
|---|---|
| OK / Lecture-Pause | lecture / pause |
| Gauche / Droite | ±10 s (plus long en maintenant) |
| Haut ou Menu | menu des effets |
| Bas | barre de lecture |
| Précédent / Suivant, Chaîne +/- | fichier précédent / suivant |
| Retour | ferme le menu, puis quitte |

Dans le menu : Haut / Bas pour choisir, Gauche / Droite pour modifier, OK pour valider.

## Obtenir l'APK
1. Poussez ce dossier à la racine d'un dépôt GitHub (branche `main`).
2. Onglet **Actions** → « Build Android TV APK » → **Run workflow**.
3. Téléchargez `app-debug.apk` dans les « Artifacts » de l'exécution.

## Installer sur la TV
- Copiez l'APK sur une clé USB, ouvrez-la avec le gestionnaire de fichiers de la TV et autorisez les
  « sources inconnues » ;
- ou
- Installez par ADB : `adb connect IP_DE_LA_TV` puis `adb install app-debug.apk`.

## Limites
- Les décodeurs sont ceux de la TV : AAC et en général Dolby (AC-3 / E-AC-3) passent ; DTS et les
  formats rares peuvent ne pas avoir de son. Un décodage logiciel complet demande l'extension FFmpeg
  de Media3 (à compiler séparément).
- Pas de sous-titres affichés, ni d'export de fichiers dans cette version.
- Les effets sont une approximation temps réel : ils ne changent ni la cadence (25 / 29,97 i/s) ni la
  forme de l'image. Sur une TV lente, baissez la « Définition d'affichage » et la « Qualité des effets ».
- Le décodage Pro Logic est une matrice simple, sans logique de direction adaptative.
- La sortie 5.1 exige que la TV accepte du PCM à 6 canaux ; sinon l'appli revient au stéréo.
