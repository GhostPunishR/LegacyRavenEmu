# RavenEmu

Bienvenue dans la documentation de la **nouvelle version multiplateforme de RavenEmu**. Le projet actif se trouve dans [`multiplateforme/`](https://github.com/GhostPunishR/RavenEmu/tree/main/multiplateforme) et utilise les moteurs C++20 du dépôt [RavenCore](https://github.com/GhostPunishR/RavenCore).

L'application installée s'appelle **RavenEmu**. Les paquets Kotlin et les noms des fichiers sont désormais sans marqueur de génération.

## Disponibilité

| Plateforme | État actuel |
|---|---|
| Android 8.0 et plus | Bêta : bibliothèque, lecteur GB/GBC/GBA, audio, commandes tactiles et sauvegardes raccordés |
| iOS | Interface partagée et passerelle native présentes ; import, persistance et lecteur jouable à connecter. Aucun binaire signé distribué |

La compatibilité GBC/GBA, les performances sur téléphone et les déblocages RetroAchievements nécessitent encore des validations. Un build réussi n'atteste pas du fonctionnement de chaque jeu.

## Commencer

1. [[Installer la bêta Android|Installation]]
2. [[Importer un jeu et lancer une partie|Premiers-pas]]
3. [[Sauvegarder et transférer sa progression|Sauvegardes-et-etats]]
4. [[Régler l'image, le son et les commandes|Affichage-et-audio]]

## Explorer

- [[Bibliothèque et ROM|Bibliotheque-et-ROM]]
- [[Consoles et compatibilité|Consoles-et-compatibilite]]
- [[Matrice des tests de jeux|Compatibilite-des-jeux]]
- [[RetroAchievements]]
- [[Dépannage|Depannage]]
- [[Compilation]], [[Architecture]] et [[Contribution]]
- [[Feuille de route|Feuille-de-route]]

## Version historique

Le code V2 0.8.1 hors de `multiplateforme/` reste dans le dépôt en héritage. Son ancien APK `test-latest` n'est plus mis à jour. Les fonctions de la V2, notamment les skins et les manettes, ne doivent pas être considérées comme automatiquement disponibles dans la nouvelle version.

La nouvelle application utilise `com.ravenemu.preview` sur Android et peut cohabiter avec la V2. Les bibliothèques, réglages et connexions sont séparés.

## Liens officiels

- [Bêta Android : workflow multiplateforme](https://github.com/GhostPunishR/RavenEmu/actions/workflows/multiplateforme.yml?query=branch%3Amain+is%3Asuccess)
- [Site officiel](https://ghostpunishr.github.io/RavenEmu/)
- [État détaillé du projet](https://github.com/GhostPunishR/RavenEmu/blob/main/multiplateforme/README.md)
- [Issues](https://github.com/GhostPunishR/RavenEmu/issues)

Aucun jeu n'est fourni. Exportez les sauvegardes importantes avant de désinstaller ou d'effacer les données de RavenEmu.

Le nouvel identifiant sépare également cette installation des anciennes préversions : exportez leurs sauvegardes avant désinstallation. Voir [[Installation]].
