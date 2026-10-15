# Passerelle native RavenEmu

Le même SDK C de [RavenCore](https://github.com/GhostPunishR/RavenCore) est utilisé sur Android et iOS. Le code C++ des moteurs n'est pas copié dans RavenEmu.

Le projet CMake télécharge les cœurs au **commit SHA précis** de la version [0.1.2](https://github.com/GhostPunishR/RavenCore/releases/tag/v0.1.2) de RavenCore ([`0e101d2`](https://github.com/GhostPunishR/RavenCore/commit/0e101d2ed6599e697409ec0a05efceb718a2a51b)), et non le tag lui-même, qui pourrait être déplacé. Le détail des changements est dans le [journal de RavenCore](https://github.com/GhostPunishR/RavenCore/blob/main/CHANGELOG.md). API C version 0 ; formats d'états GB/GBC 11 et GBA 12 (le format GBA 11 se recharge). Avant ce passage, l'épinglage était `34234f6` (lecture mémoire RetroAchievements, PR #10 de RavenCore).

- Android : `multiplateforme/native/android/bridge_jni.cpp` fournit JNI à `NativeCore.kt` et rend des images ARGB8888 avec audio PCM16.
- iOS : `multiplateforme/native/apple/module.modulemap` importe la même API C dans Swift, avec `RavenNativeCore.swift`. Le script `build-ios.sh` compile les bibliothèques C++ pour `iphoneos` ou `iphonesimulator`.
- Le dossier `sdk/` du dépôt RavenCore contient le header, l'implémentation C++ et les tests synthétiques.

Les adaptateurs natifs sont raccordés au lecteur Android RavenEmu (bibliothèque, rendu, audio, commandes, sauvegardes et RetroAchievements en validation). Le lecteur iOS complet reste à connecter.

Sur Mac (Xcode et CMake nécessaires) :

```sh
cd multiplateforme/native
sh ./build-ios.sh
```

Android (SDK/NDK nécessaires) : `./gradlew -p multiplateforme :androidApp:assembleDebug`.

**Le code natif ne contient aucune ROM ni BIOS protégé.** Les appels à une même instance doivent être sérialisés. Le frontend est responsable de la sauvegarde et de la synchronisation audio.

## API neutre et vérification de liaison

Les adaptateurs appellent `ravenemu_*`. `cmake/NeutralBridge.cmake` génère un header public normalisé et applique le même renommage aux définitions compilées du SDK épinglé, sans modifier les moteurs ni leurs formats de sauvegarde. JNI et Swift utilisent le même header généré ; la bibliothèque Android s'appelle `ravenemu_android`.

Le test hôte utilise une cartouche synthétique sans contenu de jeu et vérifie création des trois systèmes, vidéo, audio et sauvegarde/rechargement d'un état :

```sh
cmake -S multiplateforme/native -B multiplateforme/native/build-host -DCMAKE_BUILD_TYPE=Release -DRAVENEMU_BRIDGE_TESTS=ON
cmake --build multiplateforme/native/build-host --target ravenemu_bridge_smoke --parallel 2
ctest --test-dir multiplateforme/native/build-host --output-on-failure
```
