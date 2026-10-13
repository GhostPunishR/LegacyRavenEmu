# Architecture de RavenEmu

RavenEmu utilise une interface **Compose Multiplatform** commune à Android et iOS. Le projet actif est un projet Gradle indépendant dans **`multiplateforme/`** ; les moteurs C++20 vivent dans le dépôt [RavenCore](https://github.com/GhostPunishR/RavenCore).

## Modules actuels

| Module | Responsabilité |
|---|---|
| `multiplateforme/shared/` | Interface Compose, modèles, navigation, réglages et logique Kotlin commune |
| `multiplateforme/androidApp/` | Activité Android, session de jeu, audio, fichiers privés, imports et RetroAchievements |
| `multiplateforme/iosApp/` | Hôte SwiftUI, projet XcodeGen et adaptateur Swift |
| `multiplateforme/native/` | Configuration CMake, pont JNI Android et module API C pour Apple |
| `multiplateforme/policy/` | Tests JVM des règles de CI |
| `docs/` | Site officiel statique |
| `wiki/` | Pages sources du wiki GitHub |

`multiplateforme/settings.gradle.kts` inclut `shared`, `androidApp` et `policy`. Utilisez `./gradlew -p multiplateforme` pour sélectionner ce projet.

## Frontière native

La [configuration CMake](multiplateforme/native/CMakeLists.txt) récupère RavenCore à un commit précis. Mettre à jour RavenCore ne met pas automatiquement à jour RavenEmu : la révision épinglée doit être modifiée et validée séparément.

| Plateforme | Adaptateur | Données échangées |
|---|---|---|
| Android | `NativeCore.kt` et `native/android/bridge_jni.cpp` | ROM, frames ARGB8888, audio PCM16, boutons, mémoire et sauvegardes |
| iOS | `RavenNativeCore.swift` et `native/apple/module.modulemap` | Même API C, importée depuis Swift |

Les chemins natifs du tableau sont relatifs à `multiplateforme/`. L'application utilise l'API C de RavenCore, pas une interface libretro. Le catalogue Libretro est un service facultatif de jaquettes.

RetroAchievements utilise rcheevos et le pont Android maintenu dans `multiplateforme/native/android/`. Ses révisions sont également épinglées.

## Contrats à préserver

- Les moteurs ignorent l'interface et les services des plateformes.
- Les appels à une même instance native sont sérialisés.
- Android conserve la bibliothèque, les jaquettes et les sauvegardes dans son espace privé, avec le SHA-256 de la ROM comme identité.
- Les imports passent par le sélecteur Android et ne nécessitent pas d'accès global au stockage.
- Les ZIP de sauvegarde contiennent un manifeste et des empreintes, pas de ROM.
- Les identifiants `com.ravenemu.v3` et `com.ravenemu.v3.preview` restent stables pour préserver les données des testeurs.

## État des plateformes

Le lecteur Android raccorde rendu, audio, commandes, bibliothèque et sauvegardes. Les performances GBC/GBA et les déblocages RetroAchievements nécessitent encore des essais sur téléphone.

iOS dispose des écrans partagés et du pont natif ; l'import, la persistance et le lecteur jouable restent à connecter. Compiler le framework ne prouve pas une partie jouable.

## Héritage V2

Les anciens dossiers racine `app/`, `engine/`, `features/`, `platform/`, `cores/` et `native/` restent conservés, mais ne sont plus construits par la CI active.

Le détail technique de cette ancienne version est archivé dans [ARCHITECTURE-V2.md](legacy-docs/ARCHITECTURE-V2.md). Il ne décrit pas les contrats de la nouvelle application.

Voir [CONTRIBUTING.md](CONTRIBUTING.md), le [wiki Architecture](https://github.com/GhostPunishR/RavenEmu/wiki/Architecture) et le [projet multiplateforme](multiplateforme/README.md).
