# Architecture

Le projet actif est un projet Gradle indépendant dans **`multiplateforme/`**. L'interface est écrite une fois avec Compose Multiplatform ; chaque hôte apporte ses adaptateurs système.

## Modules

| Chemin | Responsabilité |
|---|---|
| `multiplateforme/shared/` | Interface Compose, modèles, navigation de bibliothèque, réglages et logique Kotlin commune |
| `multiplateforme/androidApp/` | Activité Android, lecteur, audio, fichiers privés, imports et RetroAchievements |
| `multiplateforme/iosApp/` | Hôte SwiftUI et spécification XcodeGen |
| `multiplateforme/native/` | Construction native, pont JNI Android et module API C pour Swift |
| `multiplateforme/policy/` | Tests JVM des règles de CI |
| [RavenCore](https://github.com/GhostPunishR/RavenCore) | Dépôt distinct des moteurs C++20 GB/GBC/GBA et de l'API C |

`multiplateforme/settings.gradle.kts` inclut `shared`, `androidApp` et `policy`. Les commandes utilisent `-p multiplateforme` pour sélectionner ce projet.

## Frontière native

La configuration CMake récupère RavenCore à un **commit précis**. Le code des moteurs n'est pas copié dans l'application.

| Hôte | Chemin utile | Échanges |
|---|---|---|
| Android | `NativeCore.kt` et `native/android/bridge_jni.cpp` | JNI, chargement ROM, frames ARGB8888, audio PCM16, boutons et sauvegardes |
| iOS | `RavenNativeCore.swift` et `native/apple/module.modulemap` | Même API C, appelée depuis Swift |

L'API native de l'application est l'API C de RavenCore, **pas une interface libretro**. Libretro intervient uniquement comme catalogue de jaquettes facultatif.

Le SDK rcheevos est récupéré à une révision épinglée pour l'intégration RetroAchievements Android. Le pont des succès est maintenu dans `multiplateforme/native/android/`.

## État des hôtes

Android raccorde la bibliothèque, la session native, le rendu, l'audio, les commandes et le stockage. Les opérations sur un cœur sont sérialisées ; le lecteur est suspendu en arrière-plan.

iOS affiche les écrans partagés et possède un adaptateur natif. La présence de ces fichiers ne prouve pas un lecteur complet : import, persistance et exécution jouable restent à connecter.

## Données Android

Les ROM et données sont conservées dans l'espace privé. La bibliothèque, les sauvegardes et les jaquettes utilisent le **SHA-256 de la ROM** comme identité. Les imports Android passent par le sélecteur de documents, sans accès global au stockage.

Les archives de sauvegarde contiennent un manifeste et des empreintes, pas de ROM. Les réglages sont persistants sur Android et indépendants de la V2.

## Ancienne version

Les dossiers racine `app/`, `engine/`, `features/`, `platform/`, `cores/` et `native/` appartiennent à la V2 conservée. Les modifier ne met pas automatiquement à jour la nouvelle application ou RavenCore.

Les paquets Kotlin sont `com.ravenemu.android` et `com.ravenemu.shared`, l’identifiant Android `com.ravenemu.preview` et le projet Xcode `RavenEmu`. Le changement d’identité crée une application distincte des anciennes préversions : leurs données privées ne sont pas transférées automatiquement.

## Ajouter ou modifier une fonction

- Interface ou règle partagée : `shared/`.
- Intégration Android : `androidApp/` et, si nécessaire, pont natif Android.
- Intégration Apple : `iosApp/`, branchement des callbacks partagés et pont Swift.
- Correction d'un moteur : dépôt RavenCore, puis mise à jour séparée de la révision épinglée après validation.

Sources : [projet multiplateforme](https://github.com/GhostPunishR/RavenEmu/tree/main/multiplateforme) et [configuration native](https://github.com/GhostPunishR/RavenEmu/blob/main/multiplateforme/native/CMakeLists.txt). Voir [[Compilation]] et [[Contribution]].
