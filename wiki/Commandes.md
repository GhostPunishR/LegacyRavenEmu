# Commandes

Cette page décrit le lecteur Android de la version multiplateforme.

## Boutons disponibles

| Console | Commandes tactiles |
|---|---|
| Game Boy et Game Boy Color | Croix directionnelle, A, B, Start et Select |
| Game Boy Advance | Les mêmes commandes, avec L et R |

La croix prend en charge le glissement et les diagonales. Les boutons sont relâchés quand le lecteur est fermé ou que l'application passe en arrière-plan.

## Réglages actuels

Ouvrez **Paramètres → Interface** :

- **Retour haptique** : vibration à la pression ;
- **Taille des commandes** : de 80 à 115 % ;
- **Espacement vertical** : de 20 à 80 dp ;
- **Style des boutons** : Classique ou Contraste élevé ;
- **Réinitialiser les commandes** : retour aux réglages par défaut.

Les réglages sont conservés sur Android. Le déplacement libre des boutons et les profils avancés de la V2 ne sont pas encore migrés. Le menu **⋮** du lecteur donne accès aux états, au diagnostic audio et à RetroAchievements.

## Fonctions non annoncées

Le lecteur actuel est raccordé aux commandes tactiles. Aucun parcours de configuration des manettes physiques n'est implémenté dans ce nouvel hôte Android ; ne reprenez pas les instructions de la V2 pour les utiliser.

L'import et le rendu de skins `.deltaskin` restent à migrer. Voir [[Skins de contrôleur|Skins-de-controleur]]. Aucune option d'avance rapide n'est exposée dans les réglages actuels.

## Pause

La pause automatique est fixe : le jeu et le son sont suspendus en arrière-plan et pendant l'ouverture du panneau de jeu. Elle n'est pas désactivable dans les paramètres.

Source : [réglages partagés](https://github.com/GhostPunishR/RavenEmu/blob/main/multiplateforme/shared/src/commonMain/kotlin/com/ravenemu/v3/shared/V3SettingsScreen.kt).
