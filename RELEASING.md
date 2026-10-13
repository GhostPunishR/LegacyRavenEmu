# Distribution de RavenEmu

Le canal actuel est la **bêta Android multiplateforme**, produite par le [workflow `multiplateforme.yml`](.github/workflows/multiplateforme.yml). Aucun workflow de Release numérotée ou binaire iOS signé n'est actuellement configuré.

## Artefact Android

| Élément | Valeur |
|---|---|
| Commande | `./gradlew -p multiplateforme :androidApp:assembleBeta` |
| Variante | Beta : la variante Debug (même signature) sans le drapeau débogable, pour un lecteur optimisé |
| Identifiant | `com.ravenemu.v3.preview` |
| Nom affiché | RavenEmu |
| Artefact Actions | `ravenemu-android-beta` |
| APK source | `multiplateforme/androidApp/build/outputs/apk/beta/androidApp-beta.apk` |
| Rétention | 7 jours |

Le workflow traite les pushs sur ses branches déclarées, les pull requests pour ses chemins surveillés et les déclenchements manuels. Les fichiers de documentation seuls ne relancent pas automatiquement ce build. Les filtres exacts restent dans le workflow.

Pour télécharger une bêta, choisissez une exécution réussie de `main` dans [GitHub Actions](https://github.com/GhostPunishR/RavenEmu/actions/workflows/multiplateforme.yml?query=branch%3Amain+is%3Asuccess), puis l'artefact Android. Un compte GitHub connecté est requis. Consultez le [guide d'installation](https://github.com/GhostPunishR/RavenEmu/wiki/Installation).

## Signature et mises à jour

La bêta utilise la signature Debug du build. Le workflow ne configure pas la clé Test dédiée et stable de la V2 ; une mise à jour par-dessus un autre APK n'est donc pas garantie si le certificat diffère.

Ne réutilisez pas l'empreinte du certificat V2 pour vérifier cette bêta. Relevez le commit de l'exécution et testez l'installation avant d'annoncer la compatibilité de mise à jour. Les utilisateurs doivent exporter leurs sauvegardes avant une désinstallation ou un effacement des données.

Aucun secret, keystore ou mot de passe n'est à ajouter au dépôt pour construire cette variante. Le canal ne publie pas de paire APK/fichier `.sha256` dans une GitHub Release comme le faisait l'ancienne V2.

## Avant de présenter un build aux testeurs

- Relever l'exécution, le commit et le résultat des jobs concernés.
- Tester l'installation et un scénario minimal sur appareil.
- Distinguer compilation, tests unitaires et validation réelle des jeux.
- Vérifier audio, commandes, sauvegardes, reprise et transferts pour le périmètre modifié.
- Documenter les limites, migrations et éventuelles incompatibilités de signature ou d'états.

Les jobs iOS construisent le framework partagé et le SDK natif pour simulateur. Cela ne constitue pas une distribution iOS ni un lecteur complet.

## Future Release signée

Ce canal reste à concevoir. Il nécessitera notamment une identité de version claire, une signature stable avec stockage sécurisé des clés, des artefacts vérifiables et une validation sur appareil.

Créer un tag `v*` ne déclenche pas une publication Release dans le workflow actuel. Ne suivez pas les anciennes procédures de secrets ou d'approbation comme si elles étaient actives.

## Ancienne version

`test-latest` correspond à la V2 et reste à sa dernière publication. Son workflow a été retiré. Les détails historiques sont conservés dans [RELEASING-V2.md](legacy-docs/RELEASING-V2.md), uniquement comme référence pour l'héritage.

Voir aussi [CONTRIBUTING.md](CONTRIBUTING.md) et le [wiki Compilation](https://github.com/GhostPunishR/RavenEmu/wiki/Compilation).
