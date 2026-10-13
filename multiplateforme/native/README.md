# Passerelle native V3

Le même SDK C de [RavenCore](https://github.com/GhostPunishR/RavenCore) est utilisé sur Android et iOS. Le code C++ des moteurs n'est pas copié dans la V3.

Le projet CMake télécharge les cœurs au **commit SHA précis** de la version [0.1.2](https://github.com/GhostPunishR/RavenCore/releases/tag/v0.1.2) de RavenCore ([`0e101d2`](https://github.com/GhostPunishR/RavenCore/commit/0e101d2ed6599e697409ec0a05efceb718a2a51b)), et non le tag lui-même, qui pourrait être déplacé. Le détail des changements est dans le [journal de RavenCore](https://github.com/GhostPunishR/RavenCore/blob/main/CHANGELOG.md). API C version 0 ; formats d'états GB/GBC 11 et GBA 12 (le format GBA 11 se recharge). Avant ce passage, l'épinglage était `34234f6` (lecture mémoire RetroAchievements, PR #10 de RavenCore).

- Android : `multiplateforme/native/android/bridge_jni.cpp` fournit JNI à `NativeCore.kt` et rend des images ARGB8888 avec audio PCM16.
- iOS : `multiplateforme/native/apple/module.modulemap` importe la même API C dans Swift, avec `RavenNativeCore.swift`. Le script `build-ios.sh` compile les bibliothèques C++ pour `iphoneos` ou `iphonesimulator`.
- Le dossier `sdk/` du dépôt RavenCore contient le header, l'implémentation C++ et les tests synthétiques.

Les adaptateurs natifs sont raccordés au lecteur Android V3 (bibliothèque, rendu, audio, commandes, sauvegardes et RetroAchievements en validation). Le lecteur iOS complet reste à connecter.

Sur Mac (Xcode et CMake nécessaires) :

```sh
cd multiplateforme/native
sh ./build-ios.sh
```

Android (SDK/NDK nécessaires) : `./gradlew -p multiplateforme :androidApp:assembleDebug`.

**Le code natif ne contient aucune ROM ni BIOS protégé.** Les appels à une même instance doivent être sérialisés. Le frontend est responsable de la sauvegarde et de la synchronisation audio.
