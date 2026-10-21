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

- **Kickstart** : vous choisissez le vôtre (copié dans l'application). Sinon, la ROM libre **AROS** est fournie.
- **Machines** : Amiga 1200 et Amiga 500.
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
- **Barre rapide** : ⌨ clavier, ⤓ sauvegarde et ⤒ reprise rapides, ☰ menu. **Quatre emplacements** de sauvegarde
  d'état par machine, et **deux lecteurs de disquettes** (DF0:, DF1:).
- Pause, réinitialisation, image en 4:3 ou sur toute la largeur.
- **Mesure de vitesse** : le coin de l'écran affiche les images par seconde, le pourcentage du temps réel et
  le temps de calcul d'une image. Le **Turbo** (menu) fait tourner l'Amiga sans frein : comparez les images par
  seconde avec et sans JIT.

## Comment c'est construit

| Élément | Origine |
|---|---|
| Cœur Amiberry 8.3 (WinUAE 6) | `BlitterStudio/amiberry`, même version que la version web, Makefile libretro `platform=android-arm64` (JIT ARM64 compris) |
| Correctifs du cœur | `native/patches/` : touches manquantes, manette CD32, mémoire RTG dans les sauvegardes d'état, cadence laissée au frontal |
| Frontal libretro | `amiga1200/wasm/frontend.c` (sous-module **amiga1200**, le dépôt de la version web), compilé tel quel ; `native/compat/` remplace les trois fonctions d'Emscripten qu'il utilise |
| Pont Android | `native/jni_bridge.c` : image copiée dans la surface Android, son par AAudio, appels Kotlin |
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

L'APK est signé avec une clé de test publique (`app/debug.keystore`, mot de passe `android`). C'est voulu :
chaque compilation s'installe ainsi par-dessus la précédente. Une clé privée la remplacera avant toute publication.

## Feuille de route

1. **Prototype** (ici) : cœur natif avec JIT, image, son, commandes, disquette et disque dur, mesure du gain.
2. **Interface de la version web** dans une WebView, reliée au cœur natif : Bibliothèque, configurations,
   aide, réglages, tout ce qui existe déjà.
3. **Disques** : WHDLoad en un geste, CD32, Bibliothèque.
4. **Finitions** : icône adaptative, clé de signature, publication.

## Licences

GPL v3, comme Amiberry et la version web. AROS est sous licence publique AROS. Les Kickstart d'Amiga ne sont pas
fournis : utilisez les vôtres.
