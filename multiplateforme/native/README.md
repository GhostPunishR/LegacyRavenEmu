# Passerelle native V3

Le même SDK C de [RavenCore](https://github.com/GhostPunishR/RavenCore) est utilisé sur Android et iOS. Le code C++ des moteurs n'est pas copié dans la V3.

Le projet CMake télécharge les cœurs au **commit SHA précis** [`34234f6`](https://github.com/GhostPunishR/RavenCore/commit/34234f6a3ddc488714575c53b7a01bf1f2d7b49d), après la fusion de la [PR #10](https://github.com/GhostPunishR/RavenCore/pull/10) ajoutant la lecture mémoire RetroAchievements. Le passage à la version 0.1.1 est testé séparément dans la PR RavenEmu #107. Elle apporte notamment des contrôles de sécurité, des optimisations d'états instantanés, des évolutions de l'API C et des corrections de portabilité. Les états GB/GBC sont désormais au format 11 (lecture du format 10 conservée) ; l'audio GB (fichier `apu.hpp`) est inchangé par rapport à l'ancien épinglage. Le correctif audio GBA n'est pas un correctif audio GB.

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
