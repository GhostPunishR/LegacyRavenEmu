# Contribuer à RavenEmu

Les contributions de code, de documentation et de tests sont bienvenues. Le projet actif se trouve dans **`multiplateforme/`** ; la V2 est conservée en héritage.

## Choisir le bon emplacement

| Sujet | Emplacement |
|---|---|
| Interface et logique communes Android/iOS | `multiplateforme/shared/` |
| Lecteur, stockage, audio et services Android | `multiplateforme/androidApp/` |
| Hôte Apple et intégration Swift | `multiplateforme/iosApp/` |
| Ponts natifs et révisions épinglées | `multiplateforme/native/` |
| Règles de CI | `multiplateforme/policy/` et `.github/workflows/` |
| Moteurs d'émulation | Dépôt [RavenCore](https://github.com/GhostPunishR/RavenCore) |
| Site et wiki | `docs/` et `wiki/` |

Voir [ARCHITECTURE.md](ARCHITECTURE.md). Une correction du moteur se fait dans RavenCore, puis sa révision épinglée est mise à jour séparément dans RavenEmu. Modifier les anciens modules V2 ne modifie pas la nouvelle application.

## Avant de développer

1. Recherchez une issue ou une PR similaire.
2. Pour un chantier important, décrivez le besoin, le périmètre et la validation envisagée dans une issue.
3. Créez une branche depuis `main` et gardez un objectif clair par PR.

Pour une vulnérabilité, suivez [SECURITY.md](SECURITY.md) plutôt qu'une issue publique.

## Environnement et vérifications

Utilisez JDK 21, le Gradle Wrapper du dépôt et, pour Android, le SDK API 37 avec les outils natifs CMake/NDK. Le build iOS nécessite un Mac, Xcode, CMake et XcodeGen. Les instructions détaillées sont dans le [wiki Compilation](https://github.com/GhostPunishR/RavenEmu/wiki/Compilation).

Depuis la racine du dépôt, choisissez les contrôles utiles à votre changement :

```sh
./gradlew -p multiplateforme :androidApp:testDebugUnitTest
./gradlew -p multiplateforme :androidApp:assembleDebug
./gradlew -p multiplateforme :policy:test
```

Pour découvrir les tâches du module partagé :

```sh
./gradlew -p multiplateforme :shared:tasks
```

Sur macOS, la CI vérifie aussi :

```sh
./gradlew -p multiplateforme :shared:linkDebugFrameworkIosSimulatorArm64
sh multiplateforme/native/build-ios.sh
```

Ces commandes sélectionnent le projet actuel. Les commandes Gradle sans `-p multiplateforme` à la racine ciblent la V2.

Ajoutez une vérification de régression lorsque le changement modifie un comportement. Une compilation ou un test unitaire ne remplace pas un essai sur appareil : précisez séparément ce qui a été compilé, testé et observé.

## Règles essentielles

- Respectez les frontières des modules et le style du code existant.
- Gardez les moteurs indépendants des plateformes et les appels à une instance native sérialisés.
- Préservez les données et les identifiants des testeurs ; documentez toute migration ou incompatibilité.
- Utilisez du code original et des références techniques publiques. Ne copiez, traduisez ou adaptez pas le code d'un autre émulateur.
- N'ajoutez aucune ROM commerciale, aucun BIOS protégé, aucune clé ni donnée personnelle. Les données redistribuées doivent disposer d'une licence adaptée.
- Ne présentez pas une fonction à migrer ou une validation restante comme terminée.

Les contributions relèvent de la [licence AGPL-3.0](LICENSE) et du [code de conduite](CODE_OF_CONDUCT.md). Les avis de licence des composants tiers doivent être conservés.

## Pull request

Indiquez le problème, le résultat obtenu, les modules concernés, les commandes réellement exécutées et les limites restantes. Pour une modification d'interface, ajoutez une capture sans données privées si possible.

Si une vérification n'a pas pu être effectuée, dites laquelle et pourquoi. Le [modèle de PR](.github/PULL_REQUEST_TEMPLATE.md) vous guide.

## Documentation et rapports

Le site se trouve dans `docs/`. Les pages sources du wiki sont dans `wiki/` et sont synchronisées après leur fusion dans `main`. Conservez les noms de page, actualisez `_Sidebar.md` si nécessaire et n'utilisez pas de tiret cadratin dans le wiki : son workflow le refuse.

Pour un bug ou un résultat de compatibilité, indiquez **le commit ou le lien du build**, la plateforme, l'appareil ou l'environnement, les étapes et les réglages concernés. Les noms de préversion seuls ne distinguent pas toujours les builds.

Consultez le [dépannage](https://github.com/GhostPunishR/RavenEmu/wiki/Depannage), la [matrice de compatibilité](https://github.com/GhostPunishR/RavenEmu/wiki/Compatibilite-des-jeux) et [RELEASING.md](RELEASING.md) pour la distribution actuelle.

## Intégrité de la construction

Les actions GitHub et les révisions natives sont épinglées par SHA. Le Wrapper est validé en CI et la distribution Gradle doit conserver son empreinte `distributionSha256Sum`. Lors d'une mise à jour, vérifiez les références et empreintes officielles ; ne remplacez pas le JAR du Wrapper à la main et ne versionnez aucun secret.

Les règles actives sont dans `multiplateforme/policy/`. Les procédures de la V2 sont conservées dans [les archives](legacy-docs/RELEASING-V2.md).
