# RavenEmu V3

Aperçu de la future interface **identique sur Android et iOS**. Le projet vit dans `v3/` sur la branche `feature/v3-multiplatform` et ne remplace pas encore RavenEmu Android V2.

## Ce qui fonctionne dans cette première étape

- Navigation Compose partagée entre Bibliothèque, Lecteur et Réglages.
- Recherche et filtres de consoles préparés avec tests de logique commune.
- Thème clair / sombre en mémoire, non persistant.
- Hôte Android indépendant (`com.ravenemu.v3.preview`), sans conflit avec l'application existante.
- Hôte iOS SwiftUI qui affiche le même composant Compose (projet Xcode généré par XcodeGen).

**Pas encore connecté :** chargement des ROMs, rendu des cœurs GB/GBC/GBA, audio, sauvegardes, contrôleurs, skins, RetroAchievements et stockage. Aucun écran ne prétend lancer un jeu à cette étape.

## Architecture

```text
v3/
  shared/       UI Compose et logique Kotlin Multiplatform
  androidApp/   Activité Android et packaging de prévisualisation
  iosApp/       Hôte iOS SwiftUI + spécification XcodeGen
```

Le code d'émulation reste dans [RavenEmu-Cores](https://github.com/GhostPunishR/RavenEmu-Cores). Pour la prochaine étape, une API C stable sera créée autour des moteurs C++20 ; Android l'utilisera via JNI, iOS via Kotlin/Native ou interop Swift. **Libretro n'est pas l'interface native de l'application.**

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

## Isolation et suite

Les fichiers Gradle V2 à la racine ne changent pas ; V3 possède son propre `settings.gradle.kts`. Une modification de l'application V2 ou des cœurs ne met pas automatiquement V3 à jour.

Les prochaines étapes sont : ajout d'un contrat d'émulation partagé, wrapper C multiplateforme depuis RavenEmu-Cores, tests des ROMs synthétiques sur les deux systèmes, puis migration progressive de la bibliothèque et du lecteur. L'interface doit rester la même sur les deux plateformes, avec adaptation aux tailles d'écran.
