# RavenEmu

[![RavenEmu Multiplateforme](https://github.com/GhostPunishR/RavenEmu/actions/workflows/multiplateforme.yml/badge.svg)](https://github.com/GhostPunishR/RavenEmu/actions/workflows/multiplateforme.yml)
[![Règles du dépôt](https://github.com/GhostPunishR/RavenEmu/actions/workflows/repository-policy.yml/badge.svg)](https://github.com/GhostPunishR/RavenEmu/actions/workflows/repository-policy.yml)
[![Wiki](https://img.shields.io/badge/Documentation-Wiki-5b21b6.svg?logo=github&logoColor=white)](https://github.com/GhostPunishR/RavenEmu/wiki)
[![Site officiel](https://img.shields.io/badge/Site-RavenEmu-7c3aed.svg?logo=githubpages&logoColor=white)](https://ghostpunishr.github.io/RavenEmu/)
[![APK Test](https://img.shields.io/badge/APK-Test-a855f7.svg?logo=android&logoColor=white)](https://github.com/GhostPunishR/RavenEmu/releases/download/test-latest/RavenEmu-test.apk)
[![Licence AGPL-3.0](https://img.shields.io/badge/Licence-AGPL--3.0-22c55e.svg?logo=gnu&logoColor=white)](LICENSE)

**RavenEmu** est un émulateur Game Boy, Game Boy Color et Game Boy Advance pour Android.

Les moteurs sont développés en **C++20** pour le projet à partir de documentation technique publique, avec une frontière JNI minimale vers l'application Android. RavenEmu n'intègre aucun cœur d'émulation tiers et ne fournit aucune ROM, aucun BIOS ni contenu protégé.

> **Nouvelle version en cours.** La réécriture multiplateforme (Android et iOS), qui s'appelle simplement **RavenEmu** une fois installée, se trouve dans [`multiplateforme/`](multiplateforme/README.md) ; elle utilise les cœurs du dépôt [RavenCore](https://github.com/GhostPunishR/RavenCore). La V2 0.8.1 décrite ci-dessous n'est plus construite automatiquement : son workflow `android.yml` ne se lance plus qu'à la main, et l'APK Test `test-latest` n'est donc plus mis à jour à chaque push.

## Télécharger

### [Télécharger le dernier APK Test](https://github.com/GhostPunishR/RavenEmu/releases/download/test-latest/RavenEmu-test.apk)

La CI publie l'APK Test sous `test-latest`. Il utilise le package `com.ravenemu.app.profil`. Le build Debug local utilise `com.ravenemu.app.debug` et la Release `com.ravenemu.app`.

Pour vérifier l'APK :

```bash
sha256sum -c RavenEmu-test.apk.sha256
apksigner verify --print-certs RavenEmu-test.apk
```

Empreinte SHA-256 du certificat Test :

```text
c439aed3f5210f88d92f435f949614cacbb4105ed7967a246abfa051d59feee1
```

Les détails d'installation et de signature sont dans le [wiki](https://github.com/GhostPunishR/RavenEmu/wiki/Installation).

## État du projet

| Console | État |
|---|---|
| Game Boy | Avancé |
| Game Boy Color | Intégré au moteur GB, extraction dédiée en cours |
| Game Boy Advance | Expérimental |

La compatibilité varie selon les jeux. Consultez la [matrice de compatibilité](https://github.com/GhostPunishR/RavenEmu/wiki/Compatibilite-des-jeux).

Le moteur GB/GBC distingue explicitement DMG, CGB natif et compatibilité DMG sur
CGB. Son CPU ordonne les accès par M-cycle, son PPU utilise un fetcher avec des
FIFO BG et OBJ séparés, et ses portes VRAM/OAM/CRAM suivent les transitions LCD
internes plutôt qu'une approximation par ligne. Les DMA OAM/GDMA/HDMA arbitrent
le bus progressivement et la pause `KEY1` fige les portes vidéo selon le mode
PPU où `STOP` est exécuté. Le séquenceur audio suit les fronts du diviseur
matériel en vitesse normale ou double. RavenEmu ne revendique toutefois pas
encore une émulation cycle-perfect.
Les ROMs de test de conformité restent externes au dépôt. Un manifeste
versionné contrôle leur SHA-256 et leur provenance déclarée, orchestre le runner
natif et produit un rapport JSON ; son usage est documenté dans
[`cores/gb/tests/README.md`](cores/gb/tests/README.md).

## RetroAchievements

Dans **Paramètres → RetroAchievements**, connectez un compte existant. Le profil reste mémorisé sur cet appareil après fermeture de l’application grâce au jeton chiffré ; le même dialogue est accessible depuis le menu en jeu. La catégorie RetroAchievements est la première des paramètres. En jeu, le tableau de bord affiche les points du profil, le jeu reconnu, les totaux, les succès obtenus ou verrouillés et leur progression. Chaque succès donne accès à sa description, sa date et sa page officielle. Une bannière apparaît lorsqu’un succès est détecté. Le jeu continue pendant les échanges réseau. Depuis le menu en jeu, la connexion et la déconnexion prennent effet en fermant le dialogue du compte. « Réessayer » relance la connexion et l’identification de la ROM.

L’intégration utilise le SDK officiel **rcheevos 12.5.0**, en mode **normal (Softcore)** pour GB, GBC et GBA. Hardcore et classements ne sont pas activés ; la préparation et les critères nécessaires sont décrits dans [le plan Hardcore](native/achievements/HARDCORE.md). La ROM doit être reconnue par RetroAchievements ; une ROM modifiée ou d’une autre édition peut ne pas avoir de succès. Les lectures de registres GB et certaines mémoires de cartouches spéciales ne sont pas encore exposées : les conditions qui en dépendent peuvent être désactivées par le SDK. Les restaurations d’état remettent à zéro la progression temporaire des conditions. Les succès déjà acquis restent acquis.

Le mot de passe n’est pas conservé ; le jeton de connexion est chiffré sur l’appareil. Voir [la politique de confidentialité](PRIVACY.md) et [les détails techniques et tests](native/achievements/README.md).

## Architecture

```text
app/android
    ↓
features + platform/android
    ↓
engine
    ↓
native/api + native/jni
    ↓
cores/common + cores/gb + cores/gbc + cores/gba
```

Voir [ARCHITECTURE.md](ARCHITECTURE.md) et le [wiki Architecture](https://github.com/GhostPunishR/RavenEmu/wiki/Architecture). Le dossier `docs/` est réservé au site officiel.

## Compiler

Prérequis principaux : JDK, Android SDK avec `compileSdk 37`, NDK `30.0.16248370` et CMake `3.22.1`.

```bash
git clone https://github.com/GhostPunishR/RavenEmu.git
cd RavenEmu

./gradlew test
./gradlew lint
./gradlew :app:android:assembleDebug
```

Tests C++ natifs :

```bash
cmake -S cores -B build/native-host -DRAVENEMU_BUILD_TESTS=ON
cmake --build build/native-host --parallel
ctest --test-dir build/native-host --output-on-failure
```

Le guide complet est dans le [wiki Compilation](https://github.com/GhostPunishR/RavenEmu/wiki/Compilation).

## Documentation

- [Wiki RavenEmu](https://github.com/GhostPunishR/RavenEmu/wiki)
- [Installation](https://github.com/GhostPunishR/RavenEmu/wiki/Installation)
- [Premiers pas](https://github.com/GhostPunishR/RavenEmu/wiki/Premiers-pas)
- [Compatibilité](https://github.com/GhostPunishR/RavenEmu/wiki/Compatibilite-des-jeux)
- [Sauvegardes et états](https://github.com/GhostPunishR/RavenEmu/wiki/Sauvegardes-et-etats)
- [Compilation](https://github.com/GhostPunishR/RavenEmu/wiki/Compilation)
- [Architecture](https://github.com/GhostPunishR/RavenEmu/wiki/Architecture)
- [Dépannage](https://github.com/GhostPunishR/RavenEmu/wiki/Depannage)

## Contribuer

Consultez [CONTRIBUTING.md](CONTRIBUTING.md) et [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

Le code d'un autre émulateur ne doit pas être copié, traduit ou adapté dans RavenEmu. Les tests ne doivent contenir aucune ROM commerciale ni aucun BIOS protégé.

## Licence et marques

RavenEmu est distribué sous [GNU Affero General Public License version 3](LICENSE). Copyright 2026 GhostPunishR.

Concrètement : vous pouvez utiliser, étudier, modifier et redistribuer RavenEmu, y compris à titre commercial. En contrepartie, toute version redistribuée, modifiée ou non, doit l'être sous la même licence et avec son code source. L'article 13 ajoute le cas du réseau : un utilisateur qui se servirait à distance d'une version modifiée doit pouvoir en obtenir la source. RavenEmu étant une application locale, qui se connecte à des services tiers sans en rendre aucun, cet article ne change rien à son usage habituel.

Les versions publiées jusqu'au passage à l'AGPL restaient sous licence MIT : qui les a reçues sous ces termes les conserve. Le changement vaut pour la suite.

### Composants tiers

- [rcheevos](https://github.com/RetroAchievements/rcheevos), SDK officiel RetroAchievements, sous licence MIT conservée dans [`native/achievements/LICENSE.rcheevos`](native/achievements/LICENSE.rcheevos).
- Les bibliothèques AndroidX, Material Components et Kotlin, sous licence Apache 2.0, récupérées à la construction et non versionnées ici.

Ces licences permissives autorisent l'inclusion dans une œuvre sous AGPL ; leurs avis respectifs sont conservés et ne sont pas remplacés par celle-ci.

Game Boy, Game Boy Color et Game Boy Advance sont des marques de Nintendo. RavenEmu n'est ni affilié à Nintendo, ni approuvé par Nintendo.


