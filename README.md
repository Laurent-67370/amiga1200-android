# amiga1200-android

Application Android native de l'émulateur Amiga [amiga1200](https://github.com/laurent-67370/amiga1200)
(version web : <https://laurent-67370.github.io/amiga1200/>). Elle utilise le même cœur **Amiberry (WinUAE 6)**,
compilé ici en code ARM64 avec le **JIT** : le 68040 émulé est traduit en instructions du téléphone. La version
web ne peut pas le faire, puisque WebAssembly interdit de générer du code.

> **État : prototype (étape 1).** Il sert à vérifier que le cœur tourne sur téléphone et à mesurer ce que
> le JIT apporte. L'interface complète de la version web viendra ensuite (voir la feuille de route).

## Installer la version de test

1. Sur le téléphone, ouvrez la page **[Releases → dev](https://github.com/laurent-67370/amiga1200-android/releases/tag/dev)**.
2. Téléchargez `amiga1200-android.apk`, ouvrez-le, puis autorisez l'installation d'applications de cette source.
3. Il faut Android 9 ou plus récent, sur un appareil 64 bits (ARM64).

Chaque compilation de `main` remplace cet APK. Une nouvelle version s'installe par-dessus l'ancienne et garde
les réglages.

## Ce que fait le prototype

- **Jeux WHDLoad en un geste** : une archive `.lha` de la Bibliothèque se lance d'un toucher. Le cœur Amiberry monte
  le jeu, applique ses réglages (base de jeux d'Amiberry) et garde ses sauvegardes. Il faut un vrai Kickstart.
- **Bibliothèque** : jeux WHDLoad, disquettes et disques durs déjà choisis, retrouvés d'un toucher (lus sur place).
- **Kickstart** : vous choisissez le vôtre (copié dans l'application). Sinon, la ROM libre **AROS** est fournie.
- **Machines** : Amiga 500, 600, 1200, 4000 et CD32 (CD en .iso ou .chd, avec la ROM de la console et sa manette
  à 7 boutons).
- **Quatre lecteurs de disquettes** (DF0: à DF3:).
- **Configurations enregistrées** sous un nom, **export et import .uae** (WinUAE, Amiberry, version web ; les disques
  sont retrouvés par leur nom dans la Bibliothèque).
- **JIT** (A1200) : 68040 avec FPU, vitesse maximale et 64 Mo de mémoire Fast Zorro III. Désactivez-le pour
  retrouver un A1200 d'origine (68020).
- **Disquette DF0:** (ADF, DMS, IPF…) et jusqu'à **8 disques durs** (HDF, DH0: à DH7:, AGS compris). Ils sont
  **lus et écrits sur place**, sans copie, même pour des disques de plusieurs Go. On peut changer de disquette en
  cours de partie.
- **Volume SHARED:** pour AGS et les sauvegardes WHDLoad (dossier `Android/data/io.github.laurent67370.amiga1200/files/SHARED`).
- **Carte graphique RTG** (Picasso96, uaegfx) : écrans haute résolution d'AGS et du Workbench.
- **Écran tactile** : il fait office de souris. Glisser déplace le pointeur et toucher fait un clic gauche.
  Toucher à deux doigts fait un clic droit, et un appui long suivi d'un glissement déplace une fenêtre. Le menu
  propose aussi une **manette tactile** (joystick à gauche, deux boutons à droite).
- **Clavier Amiga à l'écran** (bouton ⌨) : Esc, F1 à F10, Del, Help, touches Amiga, Ctrl, Alt, Maj
  (verrouillables), flèches et pavé numérique, libellés QWERTY ou AZERTY. Une touche tenue reste enfoncée.
- **Clavier et manettes** Bluetooth ou USB, comme la version web : Inser = Help, Page préc./suiv. = touches Amiga,
  F12 = menu. Le clavier du téléphone reste disponible (menu).
- **Écran d'accueil** en cartes : résumé de la machine, préréglages en un geste (AGS / Workbench, A1200 d'origine,
  Amiga 500), bouton « Allumer » toujours visible, aide au premier lancement.
- **Barre rapide** (s'estompe d'elle-même) : ⌨ clavier, 🖱/🕹 souris ou manette tactile, ⏸ pause, ⤓ sauvegarde et
  ⤒ reprise rapides, ☰ menu rangé par thèmes ; confirmation avant de réinitialiser ou d'éteindre. **Quatre emplacements** de sauvegarde
  d'état par machine, et **deux lecteurs de disquettes** (DF0:, DF1:).
- Pause, réinitialisation (redémarrage à froid), image en 4:3 ou sur toute la largeur.
- **Reprendre où j'en étais** : à l'extinction, l'Amiga est gardé tel quel ; l'accueil propose de le reprendre (même
  machine, mêmes disques, une seule fois).
- **JIT en un geste** : « Redémarrer sans JIT » dans le menu, pour les démos qui n'aiment pas le 68040. La machine
  choisie est retenue pour le disque de démarrage (disquette ou premier disque dur).
- **Réglages des commandes** : vitesse du pointeur, taille de la manette tactile, boutons G/D de la souris à
  l'écran, tir 1 et 2 échangés, bouton B = haut (sauter). **Deux manettes** : la seconde joue sur le port souris.
- **Rapport de plantage** : si l'émulation s'arrête brutalement, l'écran des réglages propose de partager un rapport
  (pile d'appels du cœur et fin du journal).
- **Quitter un jeu d'un geste** (bouton ⏏) : touche de sortie de WHDLoad, F10 par défaut ; un appui long choisit `*` du
  pavé numérique (Impr. écran sous WinUAE) ou Échap.
- **Touches sur la manette** : X, Y, Start, Select, L1/R1, L2/R2, L3/R3 deviennent des touches du clavier (Espace, Return,
  F1…), réglées pour chaque jeu (menu → Commandes → Touches de la manette).
- **Raccourcis de jeux** : un appui long dans la Bibliothèque pose sur l'écran d'accueil d'Android une icône qui lance le
  jeu WHDLoad, la disquette ou le CD directement.
- **Image** (accueil → Commandes, ou menu → Écran) : lisse, nette (pixels francs, agrandie sans flou) ou écran
  cathodique (lignes de balayage) ; **pixels réguliers** (agrandissement en multiple entier) ; **zoom et position
  verticale retenus pour chaque jeu**, pour retirer les bandes noires.
- **Captures d'écran** (menu → Écran) : l'image du jeu apparaît dans la Bibliothèque, une copie va dans la galerie
  (Images/Amiga1200). Un jeu sans image en reçoit une à l'extinction.
- **Sauvegardes d'état avec miniature et date** de chaque emplacement.
- **Disquette vierge** formatée (accueil → Disques, ou en cours de partie → Disquettes → Insérer), pour les jeux qui
  sauvegardent sur disquette.
- **Bruit des lecteurs de disquettes** (clic de la tête, rotation, insertion), comme sur un vrai Amiga : accueil
  → Son et MIDI, ou menu → Avancé en cours de partie.
- **MIDI** sur le port série de l'Amiga (Bars & Pipes, Music-X, OctaMED, jeux qui parlent MIDI) : l'Amiga joue sur un
  synthétiseur USB ou Bluetooth, ou sur une application de synthé d'Android, et un clavier MIDI peut jouer sur l'Amiga.
- **Icône adaptative**, monochrome sur Android 13 et plus (icônes à thème).
- **Mesure de vitesse** : le coin de l'écran affiche les images par seconde, le pourcentage du temps réel et
  le temps de calcul d'une image. Le **Turbo** (menu) fait tourner l'Amiga sans frein : comparez les images par
  seconde avec et sans JIT.

## Comment c'est construit

| Élément | Origine |
|---|---|
| Cœur Amiberry 8.3 (WinUAE 6) | `BlitterStudio/amiberry`, même version que la version web, Makefile libretro `platform=android-arm64` (JIT ARM64 compris) |
| Correctifs du cœur | `native/patches/` : touches manquantes, manette CD32, mémoire RTG dans les sauvegardes d'état, cadence laissée au frontal |
| Frontal libretro | `amiga1200/wasm/frontend.c` (sous-module **amiga1200**, le dépôt de la version web), compilé tel quel ; `native/compat/` remplace les trois fonctions d'Emscripten qu'il utilise |
| Pont Android | `native/jni_bridge.c` : image copiée dans la surface Android, son par AAudio, MIDI, appels Kotlin |
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

La clé de test passe alors le relais à la clé de publication (rotation de clé, signature APK v3). Le nouvel APK
s'installe donc par-dessus les versions de test, sans désinstaller et sans perdre les réglages ni les sauvegardes. La
clé de publication ne doit jamais entrer dans le dépôt : gardez-en une copie, car sans elle aucune mise à jour ne
pourra plus s'installer.

## Feuille de route

1. **Prototype** (ici) : cœur natif avec JIT, image, son, commandes, disquette et disque dur, mesure du gain.
2. **Interface de la version web** dans une WebView, reliée au cœur natif : Bibliothèque, configurations,
   aide, réglages, tout ce qui existe déjà.
3. **Disques** : WHDLoad en un geste, CD32, Bibliothèque.
4. **Finitions** : icône adaptative et clé de signature (faites), publication.

## Licences

GPL v3, comme Amiberry et la version web. AROS est sous licence publique AROS. Les Kickstart d'Amiga ne sont pas
fournis : utilisez les vôtres.
