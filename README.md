# amiga1200-android

Application Android native de l'émulateur Amiga [amiga1200](https://github.com/laurent-67370/amiga1200)
(version web : <https://laurent-67370.github.io/amiga1200/>). Elle utilise le même cœur **Amiberry (WinUAE 6)**,
compilé ici en code ARM64 avec le **JIT** : le 68040 émulé est traduit en instructions du téléphone. La version
web ne peut pas le faire, puisque WebAssembly interdit de générer du code.

> **État : utilisable au quotidien.** AGS, le Workbench, les jeux WHDLoad, les disquettes et la CD32 tournent sur
> téléphone, avec le JIT. L'interface est native (Kotlin) ; celle de la version web pourra venir ensuite (voir la
> feuille de route).

## Installer

1. Sur le téléphone, ouvrez la page **[Releases → dev](https://github.com/laurent-67370/amiga1200-android/releases/tag/dev)**.
2. Téléchargez `amiga1200-android.apk`, ouvrez-le, puis autorisez l'installation d'applications de cette source.
3. Il faut Android 9 ou plus récent, sur un appareil 64 bits (ARM64).

Chaque compilation de `main` remplace cet APK. Une nouvelle version s'installe par-dessus l'ancienne et garde
les réglages, la Bibliothèque et les sauvegardes.

## Ce que fait l'application

### Machines et disques
- **Machines** : Amiga 500, 600, 1200, 3000, 4000, CD32 (CD en .iso ou .chd, avec la ROM de la console et sa manette à
  7 boutons) et **CDTV** (CD en .iso ou .chd, avec un Kickstart 1.3 et l'extension du CDTV). Préréglages en un geste
  sur l'accueil : AGS / Workbench, A1200 d'origine, Amiga 500, CD32.
- **JIT** (A1200, A3000, A4000) : 68040 avec FPU traduit en code ARM64, vitesse maximale, 64 Mo de mémoire Fast Zorro III.
  « Redémarrer sans JIT » (menu) retrouve un 68020 d'origine pour les démos qui n'aiment pas le 68040 ; le choix est
  retenu pour le disque de démarrage.
- **Kickstart** : les vôtres, **plusieurs à la fois**, reconnus à leur contenu (somme CRC et version) : chacun sert
  automatiquement à la machine qui lui convient (1.3 pour l'Amiga 500 et le CDTV, 3.1 pour l'A1200, 3.1.4 et 3.2
  compris…), et l'accueil prévient si aucun ne convient. L'extension du CDTV s'ajoute de la même façon. Sinon, la ROM libre **AROS** fournie. Un Kickstart ajouté avec les jeux est installé, pas rangé
  dans la Bibliothèque.
- **Jeux WHDLoad en un geste** : une archive `.lha` de la Bibliothèque se lance d'un toucher. Le cœur Amiberry monte
  le jeu, applique ses réglages (base de jeux d'Amiberry) et garde ses sauvegardes. Il faut un vrai Kickstart.
- **Archives .zip analysées** : un disque dur (.hdf) ou un jeu WHDLoad (.lha) zippé est extrait dans
  `Android/data/io.github.laurent67370.amiga1200/files/Disques` et rangé selon ce qu'il est ; une archive incomplète
  est signalée ; les disquettes zippées restent lues telles quelles. Un petit **.hdf qui démarre WHDLoad tout seul**
  (jeu installé sur disque dur) devient un jeu : ▶ Jouer le démarre sur un A1200 avec Kickstart 3.x et 8 Mo de Fast.
- **Bibliothèque** : jeux WHDLoad, disquettes, disques durs et CD déjà choisis, retrouvés d'un toucher, avec l'image
  du jeu. Appui long : raccourci sur l'écran d'accueil d'Android, ou retrait.
- **Quatre lecteurs de disquettes** (DF0: à DF3:, ADF, DMS, IPF…), disquettes changées en cours de partie, et
  **disquette vierge** formatée pour les jeux qui sauvegardent sur disquette.
- **Jusqu'à 8 disques durs** (HDF, DH0: à DH7:, AGS compris). Disquettes et disques sont **lus et écrits sur
  place**, sans copie, même pour des disques de plusieurs Go.
- **Volume SHARED:** pour AGS et les sauvegardes WHDLoad (dossier `Android/data/io.github.laurent67370.amiga1200/files/SHARED`).
- **Carte graphique RTG** (Picasso96, uaegfx) : écrans haute résolution d'AGS et du Workbench.
- **Configurations enregistrées** sous un nom, **export et import .uae** (WinUAE, Amiberry, version web ; les disques
  sont retrouvés par leur nom dans la Bibliothèque).
- **Changer d'appareil** : *Tout exporter…* range dans un seul `.zip` les réglages, les touches de la manette, les
  configurations, les Kickstart, les sauvegardes d'état, celles des jeux (WHDLoad, SHARED:) et les captures ;
  *Importer…* sur l'autre appareil. Les jeux et disques n'y sont pas (ils sont lus sur place) : ajoutés à la
  Bibliothèque de l'autre appareil, ils sont retrouvés par leur nom. Le fichier contient vos Kickstart : gardez-le pour vous.

### Commandes
- **Écran tactile en souris** : glisser déplace le pointeur, toucher fait un clic gauche, toucher à deux doigts un
  clic droit, appui long puis glisser déplace une fenêtre. Boutons G/D à l'écran en option.
- **Manette tactile** : joystick à gauche (il part de l'endroit où le doigt se pose), deux boutons à droite.
  **Disposition à votre goût** (menu → Commandes → Disposition de la manette tactile) : boutons déplacés au doigt,
  agrandis ou retirés, touches du clavier Amiga ajoutées (Espace, F1, Return…), retenue pour un jeu ou pour tous.
- **Clavier Amiga à l'écran** (⌨) : Esc, F1 à F10, Del, Help, touches Amiga, Ctrl, Alt, Maj (verrouillables),
  flèches et pavé numérique, libellés QWERTY ou AZERTY.
- **Claviers et manettes** Bluetooth ou USB : Inser = Help, Page préc./suiv. = touches Amiga, F12 = menu. **Deux
  manettes** : la seconde joue sur le port souris (jeux à deux).
- **Touches sur la manette** : X, Y, Start, Select, L1/R1, L2/R2, L3/R3 deviennent des touches du clavier (Espace,
  Return, F1…), réglées pour chaque jeu (menu → Commandes → Touches de la manette).
- **Quitter un jeu d'un geste** (⏏) : touche de sortie de WHDLoad, F10 par défaut ; un appui long choisit `*` du pavé
  numérique (Impr. écran sous WinUAE) ou Échap.
- **Réglages des commandes** : vitesse du pointeur, taille de la manette tactile, tir 1 et 2 échangés, bouton B = haut.

### Image et son
- **Filtres** : lisse, net (pixels francs, agrandi sans flou) ou écran cathodique (lignes de balayage) ;
  **pixels réguliers** (agrandissement en multiple entier) ; image en 4:3 ou sur toute la largeur.
- **Cadrage par jeu** : zoom et position verticale retenus pour chaque jeu, pour retirer les bandes noires.
- **Captures d'écran** (menu → Écran) : l'image du jeu apparaît dans la Bibliothèque, une copie va dans la galerie
  (Images/Amiga1200). Un jeu sans image en reçoit une à l'extinction.
- **Son** sans grésillement, l'émulation cadencée par le son (musique fluide en PAL, NTSC et RTG).
- **Bruit des lecteurs de disquettes** (clic de la tête, rotation, insertion), comme sur un vrai Amiga.
- **MIDI** sur le port série de l'Amiga (Bars & Pipes, Music-X, OctaMED, jeux qui parlent MIDI) : l'Amiga joue sur un
  synthétiseur USB ou Bluetooth, ou sur une application de synthé d'Android, et un clavier MIDI peut jouer sur l'Amiga.

### Pendant la partie
- **Barre rapide** (s'estompe d'elle-même) : ⌨ clavier, 🖱/🕹 souris ou manette tactile, ⏸ pause, ⤓ sauvegarde et
  ⤒ reprise rapides, ⏏ quitter le jeu, ☰ menu rangé par thèmes (Commandes, Écran, Avancé).
- **Quatre emplacements de sauvegarde d'état** par machine, avec la miniature de l'écran et la date.
- **Reprendre où j'en étais** : à l'extinction, l'Amiga est gardé tel quel ; l'accueil propose de le reprendre (même
  machine, mêmes disques, une seule fois).
- **Réinitialisation** (redémarrage à froid) et extinction, avec confirmation.
- **Mesure de vitesse** : images par seconde, pourcentage du temps réel et temps de calcul d'une image. Le **Turbo**
  (menu → Avancé) fait tourner l'Amiga sans frein.
- **Journal** (menu → Avancé) et **rapport de plantage** : si l'émulation s'arrête brutalement, l'accueil propose de
  partager un rapport (pile d'appels du cœur et fin du journal).

### Écran d'accueil
- Cartes : préréglages, Bibliothèque, Kickstart, Disques, Machine, Commandes, Son et MIDI, Configurations, Changer
  d'appareil ; résumé de
  la machine en haut, bouton « Allumer » toujours visible, aide (?) et **assistant** (premier lancement, puis ✨) :
  ce que l'on veut faire (WHDLoad, disquettes, AGS, CD, AROS), les Kickstart qu'il faut, les fichiers, puis le geste qui lance.
- **Icône adaptative**, monochrome sur Android 13 et plus (icônes à thème).

## Comment c'est construit

| Élément | Origine |
|---|---|
| Cœur Amiberry 8.3 (WinUAE 6) | `BlitterStudio/amiberry`, même version que la version web, Makefile libretro `platform=android-arm64` (JIT ARM64 compris) |
| Correctifs du cœur | `native/patches/` : touches manquantes, manette CD32, mémoire RTG dans les sauvegardes d'état, cadence laissée au frontal |
| Frontal libretro | `amiga1200/wasm/frontend.c` (sous-module **amiga1200**, le dépôt de la version web), compilé tel quel ; `native/compat/` remplace les trois fonctions d'Emscripten qu'il utilise |
| Pont Android | `native/jni_bridge.c` : image dans la surface Android (filtres net et cathodique), son par AAudio, MIDI, fil de l'émulation cadencé par le son, appels Kotlin |
| Application | `app/` : Kotlin, sans bibliothèque externe |

L'APK est compilé par GitHub Actions (`.github/workflows/android.yml`). Les étapes sont : NDK → `native/build-core.sh`
→ `libamiga.so`, puis Gradle → APK, publié comme artefact et, depuis `main`, dans la release `dev`.

Pour compiler en local (Android SDK et NDK installés) :

```sh
git clone --recurse-submodules https://github.com/laurent-67370/amiga1200-android.git
cd amiga1200-android
ANDROID_NDK_ROOT=~/Android/Sdk/ndk/<version> native/build-core.sh
gradle assembleRelease      # Gradle 8.14, JDK 17
```

Gradle signe l'APK avec une clé de test publique (`app/debug.keystore`, mot de passe `android`). Si le dépôt a une
**clé de publication** dans ses secrets (Settings → Secrets and variables → Actions), `scripts/sign-apk.sh` signe
ensuite l'APK avec elle :

| Secret | Contenu |
|---|---|
| `SIGNING_KEYSTORE_B64` | magasin de clés PKCS12 en base64 (alias `amiga1200`) |
| `SIGNING_PASSWORD` | son mot de passe |

C'est le cas depuis la compilation 52 (23/10/2026) : l'APK publié est signé avec la clé de publication (empreinte SHA-256
`0F:FD:CF:CB:C3:68:36:4E:C0:44:D9:11:80:D6:AB:F6:3A:7B:4D:A4:9B:E2:52:48:60:F9:95:B6:F7:EB:54:7E`).

La clé de test passe alors le relais à la clé de publication (rotation de clé, signature APK v3). Le nouvel APK
s'installe donc par-dessus les versions de test, sans désinstaller et sans perdre les réglages ni les sauvegardes. La
clé de publication ne doit jamais entrer dans le dépôt : gardez-en une copie, car sans elle aucune mise à jour ne
pourra plus s'installer.

## Feuille de route

1. **Cœur natif** avec JIT, image, son, commandes, disquettes et disques durs : fait.
2. **Disques et confort** : WHDLoad en un geste, CD32, Bibliothèque, sauvegardes, filtres d'image, MIDI : fait.
3. **Finitions** : icône adaptative et clé de publication : faites.
4. **Publication** (Google Play ou F-Droid).
5. Éventuellement, **interface de la version web** dans une WebView, reliée au cœur natif.

## Licences

GPL v3, comme Amiberry et la version web. AROS est sous licence publique AROS. Les Kickstart d'Amiga ne sont pas
fournis : utilisez les vôtres.
