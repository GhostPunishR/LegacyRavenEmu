# Préparation du mode Hardcore

Statut : **non activé**. La catégorie Android expose ce statut sans interrupteur
qui laisserait croire à une prise en charge. Les points Hardcore du profil
proviennent du SDK ; ils ne signifient pas que cette session est Hardcore.
Le client force `rc_client_set_hardcore_enabled(rc, 0)` et le test de déblocage
vérifie que la requête du SDK contient `h=0`.

L’activation nécessite une implémentation séparée des protections, puis la
validation de l’intégration selon les exigences de RetroAchievements. Ne pas
simplement modifier le drapeau du SDK.

## Points d’intégration et critères avant activation

- Démarrage : interdire toute reprise automatique ou explicite d’un état avant
  le démarrage Hardcore. Attendre l’identification du jeu et la session SDK.
- Transitions : traiter l’événement `RC_CLIENT_EVENT_RESET` et redémarrer le
  cœur à froid lors du passage en Hardcore. Une connexion au milieu d’une
  partie Softcore ne doit pas transformer cette partie en Hardcore.
- États : bloquer les restaurations dans tous les chemins (menu, bibliothèque,
  reprise automatique, appels à `loadState`), pas seulement masquer le menu.
  Contrôler la politique de sauvegarde/suspension avec RetroAchievements.
- Fonctions du cœur : vérifier l’absence de triche, rewind, ralentissement,
  avancée image par image et mutations mémoire de diagnostic pendant la session.
- Pause et réseau : traiter les restrictions de pause du SDK et les erreurs
  d’authentification, sans changer de mode silencieusement.
- Classements : traiter début, suivi, annulation et soumission SDK avec les
  indicateurs visibles. Ils restent indisponibles dans la version actuelle.
- Validation : tests des transitions et de chaque accès aux états, tests sur
  appareil GB/GBC/GBA avec jeux reconnus, contrôle des lectures mémoire,
  approbation RetroAchievements et vérification du binaire distribué.

Références : [intégration officielle](https://github.com/RetroAchievements/rcheevos),
API `rc_client.h` de la version épinglée dans `CMakeLists.txt`,
[documentation RetroAchievements](https://docs.retroachievements.org/).

## Parcours manuel du tableau de bord Softcore

1. Sans compte, ouvrir RetroAchievements en jeu ; connecter un compte, fermer
   le dialogue et attendre le chargement. Le jeu doit rester en pause.
2. Vérifier le profil, les points, le titre reconnu, les totaux et les filtres.
3. Débloquer un succès, puis consulter son détail et sa date dans « Obtenus ».
4. Couper le réseau : vérifier le statut et les succès en attente de
   synchronisation ; rétablir le réseau sans quitter la session.
5. Déconnecter le compte : aucun nouveau succès ne doit être évalué ; reconnecter
   ou utiliser « Réessayer » sans relancer la ROM.
6. Ouvrir un lien de succès, revenir à l’application : elle reste en pause tant
   que le tableau de bord est ouvert. Fermer/recréer l’activité sans crash.

Limites restantes : aucune persistance des envois en attente après fermeture,
aucun cache des badges/avatars, pas de consultation native des classements,
et certaines régions mémoire GB/cartouches spéciales restent indisponibles.
Le lien du profil donne accès aux informations complètes du compte sur le site.
