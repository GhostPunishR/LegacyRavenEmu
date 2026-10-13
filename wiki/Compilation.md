# Compilation

**Toutes les commandes de cette page ciblent `multiplateforme/`.** Les anciens projets Gradle et CMake de la V2 restent en héritage hors de ce dossier et ne sont plus construits par la CI active.

## Prérequis

- Git et le Gradle Wrapper du dépôt ;
- JDK 21, comme dans la CI ;
- Android SDK avec API 37 pour le build Android ;
- outils Android CMake/NDK adaptés au build natif ;
- accès aux dépendances Gradle, à RavenCore et à rcheevos lors du premier téléchargement.

La configuration native exige CMake 3.22.1 ou plus. Le projet multiplateforme ne fixe pas le même numéro de NDK que l'ancien projet V2 : ne recopiez pas ses prérequis comme un épinglage actuel.

## Récupérer le projet

```sh
git clone https://github.com/GhostPunishR/RavenEmu.git
cd RavenEmu
```

## Android

Depuis la racine de RavenEmu :

```sh
./gradlew -p multiplateforme :androidApp:assembleDebug
```

APK produit :

```text
multiplateforme/androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Pour les tests unitaires Android et les règles du dépôt, la CI utilise :

```sh
./gradlew -p multiplateforme :androidApp:testDebugUnitTest
./gradlew -p multiplateforme :policy:test
```

Les tests `policy` sont des tests JVM de fichiers et workflows, sans appareil. Les tests unitaires ne prouvent pas l'exécution correcte d'un jeu sur téléphone.

Les tâches du module partagé dépendent de l'environnement et des cibles disponibles. Pour les découvrir :

```sh
./gradlew -p multiplateforme :shared:tasks
```

Ne remplacez pas ces commandes par `./gradlew test` à la racine : cela vise l'ancien projet.

## SDK Android

Configurez le SDK avec les mécanismes Android/Gradle, par exemple `ANDROID_HOME` ou le fichier `local.properties` adapté au projet. Ne versionnez jamais vos chemins locaux, clés ou mots de passe.

Un APK Debug local utilise la signature Debug de votre environnement. Elle peut différer de celle d'un artefact CI ; voir [[Installation]]. Aucune procédure de Release publique signée n'est configurée dans le workflow actuel.

## iOS sur macOS

Prérequis supplémentaires : Xcode, ses outils de compilation, CMake 3.22.1 ou plus et [XcodeGen](https://github.com/yonaskolb/XcodeGen). L'hôte cible iOS 15.0 ou plus.

Depuis la racine du dépôt :

```sh
./gradlew -p multiplateforme :shared:linkDebugFrameworkIosSimulatorArm64
sh multiplateforme/native/build-ios.sh
```

Pour générer et ouvrir l'hôte :

```sh
cd multiplateforme/iosApp
xcodegen generate
open RavenEmuV3.xcodeproj
```

Le nom technique du projet Xcode est conservé ; le nom affiché de l'application est RavenEmu. Les scripts Xcode construisent la partie native et intègrent `RavenShared`.

Le framework et le SDK natif peuvent compiler sans que le lecteur iOS soit jouable : import, persistance et boucle du lecteur restent à raccorder. Aucun binaire iOS signé n'est distribué.

## Référence CI

Le [workflow multiplateforme](https://github.com/GhostPunishR/RavenEmu/blob/main/.github/workflows/multiplateforme.yml) construit l'APK Android, exécute ses tests, construit le framework iOS et le SDK natif pour simulateur. Les règles vérifient notamment les actions épinglées et le Wrapper Gradle.

Voir [[Architecture]] et [[Contribution]].
