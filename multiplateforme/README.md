# RavenEmu — multiplateforme

RavenEmu partage son interface Compose Multiplatform entre Android et iOS. Le projet actif se trouve dans `multiplateforme/` ; ses paquets Kotlin sont `com.ravenemu.android` et `com.ravenemu.shared`. Le workflow `.github/workflows/multiplateforme.yml` construit l'APK bêta `ravenemu-android-beta`, les tests Android, les règles du dépôt et le framework partagé iOS.

## Fonctionnalités

Sur Android, la bibliothèque privée conserve les ROM Game Boy, Game Boy Color et Game Boy Advance par empreinte SHA-256. Elle propose l'import d'un fichier ou d'un dossier, les jaquettes manuelles ou en ligne sur option, la recherche et les vues grille/liste. Les cœurs RavenCore fournissent la vidéo, l'audio PCM16 et les commandes tactiles ; la cadence du lecteur tourne sur un thread dédié.

Les sauvegardes batterie sont persistantes et les états disposent de quatre emplacements avec miniatures. Le menu du jeu permet l'export et l'import de fichiers `.sav` ou d'archives ZIP, sans inclure de ROM. Le format exporté s'appelle `RavenEmu-GB-Saves` ; les archives historiques restent acceptées après contrôle de la ROM, des tailles et des empreintes.

Les paramètres partagés regroupent les réglages audio, vidéo, interface et les documents légaux. Android raccorde les skins Delta et RetroAchievements via le SDK officiel rcheevos, avec jeton chiffré dans Android Keystore. Le mot de passe n'est pas conservé. L'authentification, la reconnaissance des jeux, les badges et les déblocages synchronisés restent à valider sur téléphone avec un compte et une ROM reconnue.

L'hôte iOS affiche les écrans partagés et dispose d'un adaptateur Swift vers l'API native. L'import, la persistance et le lecteur iOS complet restent à raccorder. Aucun binaire iOS signé n'est distribué.

## Identité et données

L'application installée s'appelle **RavenEmu**. L'identifiant Android est `com.ravenemu.preview` ; l'identifiant iOS est `com.ghostpunishr.ravenemu`. Les noms des préférences privées, caches et clés Keystore sont également sans marqueur de génération.

**Le changement d'identifiant crée une application distincte des anciennes préversions.** Android ne transfère pas automatiquement leur bibliothèque privée, leurs réglages ni leur connexion RetroAchievements. Avant de supprimer une ancienne installation, exportez ses sauvegardes. Réimportez les ROM et les sauvegardes dans la nouvelle application et reconnectez le compte si nécessaire. Une archive ZIP historique ou un `.sav` compatible reste importable ; les formats natifs des états restent ceux du même RavenCore épinglé.

## Architecture

| Dossier | Rôle |
| --- | --- |
| `shared/` | Interface Compose et logique Kotlin Multiplatform |
| `androidApp/` | Activité Android, persistance et packaging |
| `iosApp/` | Hôte SwiftUI et spécification XcodeGen |
| `native/` | API C neutre, JNI, import Swift et liaison RavenCore |
| `policy/` | Tests JVM des règles de CI |

Les moteurs restent dans [RavenCore](https://github.com/GhostPunishR/RavenCore), épinglé au commit `0e101d2ed6599e697409ec0a05efceb718a2a51b` de la version 0.1.2. Les adaptateurs utilisent les noms C `ravenemu_*`. CMake normalise ensemble les déclarations du header et les symboles compilés du SDK ; il ne modifie ni le dépôt distant ni la logique des moteurs. Les formats d'états restent GB/GBC 11 et GBA 12, ce dernier relisant le format 11.

Le code Android historique situé hors de ce dossier est conservé dans le dépôt actuel et n'est plus construit par la CI active. Le projet multiplateforme possède son propre `settings.gradle.kts`. Pour le déplacer dans un dépôt autonome, il faudra aussi emporter le Gradle Wrapper, les documents légaux et le workflow, puis ajuster leurs chemins.

## Android

Prérequis : JDK 21, Android SDK API 37 et NDK/CMake. Depuis la racine du dépôt :

```sh
./gradlew -p multiplateforme :androidApp:assembleDebug
./gradlew -p multiplateforme :androidApp:testDebugUnitTest
./gradlew -p multiplateforme :policy:test
```

APK Debug : `multiplateforme/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

La CI publie la variante bêta non débogable, signée comme le build Debug :

```sh
./gradlew -p multiplateforme :androidApp:assembleBeta
```

APK bêta : `multiplateforme/androidApp/build/outputs/apk/beta/`.

## iOS (macOS)

Prérequis : Xcode, JDK 21 et [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```sh
cd multiplateforme/iosApp
xcodegen generate
open RavenEmu.xcodeproj
```

Le projet Xcode construit les archives natives et intègre `RavenShared` avec le Gradle Wrapper. Pour vérifier seulement le framework depuis la racine du dépôt :

```sh
./gradlew -p multiplateforme :shared:linkDebugFrameworkIosSimulatorArm64
```

Voir [la passerelle native](native/README.md) pour les prérequis et le test de liaison C. Une compilation réussie ne remplace pas les essais audio, vidéo, sauvegardes et RetroAchievements sur appareil réel.
