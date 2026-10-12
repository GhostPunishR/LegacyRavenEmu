# RavenEmu V3

Aperçu de la future interface **identique sur Android et iOS**. Le projet vit dans `v3/` sur la branche `feature/v3-multiplatform` et ne remplace pas encore RavenEmu Android V2.

## État de la V3

- Une interface Compose commune Android/iOS pour Bibliothèque, Lecteur et Réglages.
- Une passerelle C native reliant les moteurs maison GB/GBC/GBA, avec adaptateurs Android JNI et Swift.
- **Étape 1 :** import d'un fichier `.gb` depuis le sélecteur Android et chargement dans le vrai cœur RavenGB. L'écran confirme le nom et les dimensions de la ROM si le chargement réussit.
- Le fichier n'est chargé **qu'en mémoire**. Il n'y a pas encore de bibliothèque persistante.
- Les fichiers vides, non `.gb`, invalides ou supérieurs à 16 Mio sont refusés. Un échec conserve la session précédente si elle existe.

**Encore à réaliser :** rendu en temps réel, commandes, audio, sauvegardes disque, import iOS, autres consoles dans le lecteur et RetroAchievements. Une ROM chargée n'est pas une partie jouable.

## Architecture

```text
v3/
  shared/       UI Compose et logique Kotlin Multiplatform
  androidApp/   Activité Android et packaging de prévisualisation
  iosApp/       Hôte iOS SwiftUI + spécification XcodeGen
```

Le code d'émulation reste dans [RavenEmu-Cores](https://github.com/GhostPunishR/RavenEmu-Cores). Une API C de préversion existe désormais pour les moteurs C++20, utilisée côté Android par JNI et côté iOS par Swift. **Libretro n'est pas l'interface native de l'application.**

## Android

Prérequis : JDK 21, Android SDK avec API 37, Gradle Wrapper du dépôt.

À partir de la racine de RavenEmu :

```sh
./gradlew -p v3 :androidApp:assembleDebug
```

APK : `v3/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

Pour exécuter les tests Kotlin communs, utilisez les tâches de test disponibles sur votre plateforme et `./gradlew -p v3 :shared:tasks`.

## iOS (sur macOS)

Prérequis : Xcode, les outils de compilation iOS, JDK 21 et [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```sh
cd v3/iosApp
xcodegen generate
open RavenEmuV3.xcodeproj
```

Sélectionnez le simulateur iOS et démarrez l'application depuis Xcode. Le script de construction de l'hôte compile et intègre `RavenShared` via le Gradle Wrapper du dépôt. Pour tester seulement le framework :

```sh
./gradlew -p v3 :shared:linkDebugFrameworkIosSimulatorArm64
```

L'application iOS et la compilation Apple nécessitent un Mac avec Xcode. Aucun binaire iOS signé n'est encore distribué.

## Passerelle native GB / GBC / GBA

La base native est disponible dans [`native/`](native/README.md). Elle relie les trois cœurs maison depuis un commit épinglé de [RavenEmu-Cores](https://github.com/GhostPunishR/RavenEmu-Cores/pull/3).

- **Android** : `NativeCore.kt` charge la bibliothèque JNI `ravenemu_v3_android`. Elle permet de charger des octets de ROM et de lire les frames ARGB8888, l'audio stéréo et les boutons.
- **iOS** : `RavenNativeCore.swift` importe la même API C compilée pour l'architecture Apple. Le script `sh v3/native/build-ios.sh` produit les archives C++ nécessaires.

L'import GB Android charge maintenant les ROMs dans le cœur natif, mais l'exécution à cadence réelle et le rendu interactif ne sont pas raccordés au lecteur. Une compilation native réussie n'atteste pas encore de l'exécution sur un appareil iOS.

## Isolation et suite

Les fichiers Gradle V2 à la racine ne changent pas ; V3 possède son propre `settings.gradle.kts`. Une modification de l'application V2 ou des cœurs ne met pas automatiquement V3 à jour.

Prochaines étapes, dans cet ordre : affichage du framebuffer GB sur Android, commandes tactiles, audio, sauvegardes persistantes, puis import et lecteur iOS, et enfin GBC/GBA. L'interface doit rester la même sur les deux plateformes, avec adaptation aux tailles d'écran.
