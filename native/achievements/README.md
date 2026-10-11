# RetroAchievements

SDK officiel [rcheevos 12.5.0](https://github.com/RetroAchievements/rcheevos/tree/1433173220a7eaede6a9ed7a18e94117be1821e0), licence MIT conservée dans `LICENSE.rcheevos`. CMake récupère cette révision immuable ; une première compilation nécessite l’accès à GitHub. Pour un SDK déjà téléchargé : `-DFETCHCONTENT_SOURCE_DIR_RCHEEVOS=/chemin/rcheevos`.

Le SDK reconnaît les jeux par leur empreinte, interprète les conditions, gère les déblocages, la présence et les nouvelles tentatives réseau. L’intégration ne contient aucun code d’un autre émulateur. Elle utilise l’API client officielle, pas l’API Web publique pour simuler des déblocages. Hardcore est explicitement désactivé à la création ; aucun classement Hardcore n’est proposé.

## Threads et durée de vie

Chaque client appartient à un seul thread : celui de la session, ou le worker du dialogue de connexion. Le SDK produit des requêtes copiées dans une file ; deux workers HTTPS envoient les requêtes. Leurs réponses reviennent dans une file et les callbacks du SDK s’exécutent sur le thread propriétaire, avec accès à la mémoire courante. Une réponse HTTP tardive ne touche jamais un client natif détruit.

`onEmulationTick` évalue chaque trame, même sans rendu vidéo. En pause, `rc_client_idle` traite les nouvelles tentatives sans avancer les conditions. Reset et restauration d’état réussie réinitialisent les conditions temporaires ; les états RavenEmu n’incorporent pas encore les compteurs du SDK. Les erreurs RA sont isolées de la partie.

Le transport impose HTTPS vers `retroachievements.org`, sans redirection, avec des délais bornés, une limite de réponse et un User-Agent RavenEmu. Ni identifiants ni corps de requête ne sont journalisés. Le jeton est chiffré avec AES-GCM/Android Keystore dans `noBackupFilesDir`. La déconnexion et un changement de compte prennent effet au prochain lancement du jeu.

## Mémoire exposée

Les adresses suivent `src/rcheevos/consoleinfo.c` du SDK :

- GB/GBC : ROM bancarisée 0000–7FFF, VRAM banque 0, RAM cartouche physique banque 0, WRAM banques 0/1 fixes et miroir, OAM, HRAM, IE. GBC : banques WRAM 2–7 à 10000–15FFF ; RAM cartouche banques 1–15 à 16000–33FFF.
- GBA : IWRAM à 00000–07FFF, EWRAM à 08000–47FFF, les premiers 64 Kio de mémoire de sauvegarde à 48000–57FFF (limités à la taille réelle).

Les lectures consultent le stockage directement : aucun cycle CPU, effet de registre, commande Flash/EEPROM ou changement de banque. Les régions absentes, les registres GB et les mémoires spéciales non exposées renvoient une lecture incomplète. Le SDK peut désactiver les succès qui en dépendent ; l’intégration ne prétend pas prendre en charge ces conditions. Les lectures des cartouches MBC7 et des zones Flash MBC6 ne sont pas exposées au-delà de leur RAM commune.

## Vérification

```sh
cmake -S native -B build/achievements-host -DRAVENEMU_BUILD_ACHIEVEMENT_TESTS=ON
cmake --build build/achievements-host --target ravenemu_achievement_tests --parallel
build/achievements-host/achievements/ravenemu_achievement_tests
```

Les tests n’utilisent ni compte ni ROM commerciale : ROMs synthétiques, serveur simulé, reconnaissance par hash, chargement des conditions et déblocage réel par le SDK en réponse à une écriture CPU. Ils vérifient le mode Softcore, l’unicité du déblocage après reset, les banques CGB fixes, les limites et l’absence d’effets de bord des lectures mémoire.

Avant validation sur appareil : connecter un compte de test, ouvrir une ROM reconnue, obtenir un succès, vérifier sa présence sur le site, essayer pause/reprise, restauration d’état, coupure puis retour du réseau, fermeture pendant une requête et déconnexion. Les envois en attente ne persistent pas après fermeture. Aucun résultat de test synthétique ne valide à lui seul le service en production ou la compatibilité d’un jeu commercial.
