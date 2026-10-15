# Installation

Cette page concerne la nouvelle version multiplateforme. **L'ancien APK `test-latest` appartient à la V2 et n'est plus mis à jour.**

## Télécharger la bêta Android

Prérequis : Android 8.0 ou plus récent et un compte GitHub connecté pour télécharger un artefact Actions.

1. Ouvrez le [workflow RavenEmu Multiplateforme](https://github.com/GhostPunishR/RavenEmu/actions/workflows/multiplateforme.yml?query=branch%3Amain+is%3Asuccess).
2. Choisissez une exécution réussie de la branche `main`, avec le job **Android (APK bêta)** réussi.
3. Dans la zone **Artifacts**, téléchargez **`ravenemu-android-beta`**.
4. Extrayez l'archive ZIP pour récupérer l'APK.
5. Ouvrez l'APK sur Android et autorisez l'installation depuis cette source si le système le demande.

Les artefacts sont conservés **7 jours**. S'il n'y en a plus, consultez les exécutions plus récentes ou [[compilez l'application|Compilation]].

## Identifier la bonne application

| Élément | Nouvelle version |
|---|---|
| Nom affiché | RavenEmu |
| Identifiant Android | `com.ravenemu.preview` |
| Distribution | Artefact GitHub Actions `ravenemu-android-beta` |
| Variante construite | Debug, avec moteurs natifs Android optimisés |

L'application peut cohabiter avec la V2 ; elle ne remplace pas ses données. Après installation, continuez avec [[Premiers pas|Premiers-pas]].

## Provenance et signature

Téléchargez depuis le workflow officiel et relevez le commit de l'exécution. L'empreinte du certificat Test de la V2 **ne s'applique pas** à cette bêta.

Le workflow actuel construit `assembleDebug` et ne configure pas la clé Test dédiée et stable de l'ancienne version. Ne supposez donc pas qu'un APK provenant d'une nouvelle exécution pourra toujours mettre à jour l'installation précédente.

Si Android indique une signature incompatible, exportez d'abord toutes vos sauvegardes depuis l'application installée. Une désinstallation ou un effacement des données supprime la bibliothèque privée, les réglages et les sauvegardes internes. Voir [[Sauvegardes et états|Sauvegardes-et-etats]].

Une autorisation d'installation hors magasin n'est pas un diagnostic de sécurité. Si Android signale explicitement une application nuisible, ne contournez pas l'alerte ; signalez le message et la provenance du fichier au projet.

## iOS

Aucun binaire iOS signé ni téléchargement public n'est distribué. Sur Mac, les développeurs peuvent construire l'hôte et le framework partagé, mais l'import et le lecteur jouable ne sont pas encore raccordés. Voir [[Compilation]].

## Jeux

Aucune ROM ni aucun contenu protégé n'est fourni. Importez des copies que vous êtes autorisé à utiliser ou des homebrews librement distribués. Aucun fichier BIOS externe n'est demandé par le lecteur GBA actuel.

Source : [workflow multiplateforme](https://github.com/GhostPunishR/RavenEmu/blob/main/.github/workflows/multiplateforme.yml) et [configuration Android](https://github.com/GhostPunishR/RavenEmu/blob/main/multiplateforme/androidApp/build.gradle.kts).

Le nouvel identifiant crée aussi une installation distincte des anciennes préversions. Exportez leurs sauvegardes avant désinstallation, puis réimportez les ROM et sauvegardes et reconnectez RetroAchievements. Les archives historiques restent importables.
