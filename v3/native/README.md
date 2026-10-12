# Passerelle native V3

Le même SDK C de [RavenEmu-Cores](https://github.com/GhostPunishR/RavenEmu-Cores) est utilisé sur Android et iOS. Le code C++ des moteurs n'est pas copié dans la V3.

Le projet CMake télécharge les cœurs à un **commit SHA précis** de `main` : [`9a99ee9`](https://github.com/GhostPunishR/RavenEmu-Cores/commit/9a99ee94f488fa34aff1efa90ce5c17e18a2f14e), qui inclut la [PR #3](https://github.com/GhostPunishR/RavenEmu-Cores/pull/3) et la [PR #4](https://github.com/GhostPunishR/RavenEmu-Cores/pull/4). Les tests natifs, Libretro et ASan/UBSan ont réussi avant la mise à jour. Ce commit ajoute notamment la validation des save states ; le correctif audio GBA n'est pas un correctif audio GB.

- Android : `v3/native/android/bridge_jni.cpp` fournit JNI à `NativeCore.kt` et rend des images ARGB8888 avec audio PCM16.
- iOS : `v3/native/apple/module.modulemap` importe la même API C dans Swift, avec `RavenNativeCore.swift`. Le script `build-ios.sh` compile les bibliothèques C++ pour `iphoneos` ou `iphonesimulator`.
- Le dossier `sdk/` du dépôt RavenEmu-Cores contient le header, l'implémentation C++ et les tests synthétiques.

À ce stade, ce sont des **adaptateurs moteurs**, pas un lecteur complet. La bibliothèque, le sélecteur de ROMs, les rendus temps réel, le système audio, les commandes tactiles, les sauvegardes disque et RetroAchievements ne sont pas encore raccordés à l'UI partagée.

Sur Mac (Xcode et CMake nécessaires) :

```sh
cd v3/native
sh ./build-ios.sh
```

Android (SDK/NDK nécessaires) : `./gradlew -p v3 :androidApp:assembleDebug`.

**Le code natif ne contient aucune ROM ni BIOS protégé.** Les appels à une même instance doivent être sérialisés. Le frontend est responsable de la sauvegarde et de la synchronisation audio.
