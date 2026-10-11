# RetroAchievements

SDK officiel [rcheevos 12.5.0](https://github.com/RetroAchievements/rcheevos/tree/1433173220a7eaede6a9ed7a18e94117be1821e0), licence MIT conservée dans `LICENSE.rcheevos`. CMake récupère cette révision immuable ; une première compilation nécessite l’accès à GitHub. Pour un SDK déjà téléchargé : `-DFETCHCONTENT_SOURCE_DIR_RCHEEVOS=/chemin/rcheevos`.

Le SDK reconnaît les jeux par leur empreinte, interprète les conditions, gère les déblocages, la présence et les nouvelles tentatives réseau. L’intégration ne contient aucun code d’un autre émulateur. Elle utilise l’API client officielle, pas l’API Web publique pour simuler des déblocages. Hardcore reste désactivé à la création : aucun déblocage Hardcore n'est proposé. Les événements de classements, de défis actifs, de progression mesurée et de trackers sont maintenant transmis à l'interface, et la Rich Presence est affichée dans le tableau de bord. Tous ces parcours nécessitent des tests manuels sur des jeux reconnus.

## Threads et durée de vie

Chaque client appartient à un seul thread : celui de la session, ou le worker du dialogue de connexion. Le SDK produit des requêtes copiées dans une file ; deux workers HTTPS envoient les requêtes. Leurs réponses reviennent dans une file et les callbacks du SDK s’exécutent sur le thread propriétaire, avec accès à la mémoire courante. Une réponse HTTP tardive ne touche jamais un client natif détruit. À la fermeture, les callbacks en attente reçoivent une réponse locale terminale et les tentatives différées sont vidées en avançant l’horloge du SDK de trois minutes ; aucun nouvel envoi réseau n’est lancé.

`onEmulationTick` évalue chaque trame, même sans rendu vidéo. En pause, `rc_client_idle` traite les nouvelles tentatives sans avancer les conditions. Reset et restauration d’état réussie réinitialisent les conditions temporaires ; les états RavenEmu n’incorporent pas encore les compteurs du SDK. Les erreurs RA sont isolées de la partie. Une file chiffrée sur l'appareil conserve les requêtes de succès Softcore authentiques émises par rcheevos avant leur envoi et tente de les réexpédier après connexion du même compte. Elle est effacée en cas de déconnexion explicite. Les demandes ne sont jamais fabriquées à partir des identifiants de succès. Une validation sur appareil en coupure réseau et après redémarrage reste nécessaire.

Le transport impose HTTPS vers `retroachievements.org`, sans redirection, avec des délais bornés, une limite de réponse et un User-Agent RavenEmu. Ni identifiants ni corps de requête ne sont journalisés. Le jeton est chiffré avec AES-GCM/Android Keystore dans `noBackupFilesDir`. Depuis le tableau de bord en jeu, la déconnexion et un changement de compte ferment puis remplacent le client sur le thread de session. La ROM est conservée en mémoire pendant la partie pour permettre une nouvelle identification sans relancer le cœur. Les instantanés du tableau de bord copient les données SDK sur ce même thread, sans jeton, puis les affichent sur le thread UI.

## Profil et badges

Le SDK publie le portrait du compte et le badge de chaque succès, en couleur une fois obtenu et grisé avant. Quand le serveur ne les nomme pas, le SDK compose l'adresse à partir du nom ; l'adaptateur s'appuie sur ce calcul plutôt que d'écrire un hôte en dur. Ces adresses traversent le pont avec le reste : onzième colonne du profil, neuvième de chaque succès, cinquième de chaque événement.

Une adresse venue d'une réponse serveur décide de l'hôte que l'application ira contacter. Le filtre `AchievementImageUrl` n'accepte que HTTPS vers `retroachievements.org` et ses sous-domaines, sans identifiant, sans port inhabituel, sans paramètre ni fragment. Le téléchargement est borné en octets et le décodage en pixels ; une réponse qui n'est pas une image ne donne rien. Deux caches évitent de redemander la même image, l'un en mémoire pour la session, l'autre dans le cache de l'application.

La carte de profil montre le nom affiché, le nom de compte, le portrait et les points des deux modes. Le **rang**, la **date d'inscription** et la **dernière activité** que porte la page du site n'y sont pas : ils ne sont pas dans l'API client, seulement dans l'API Web, qui demande une clé de développeur distincte du compte joueur. RavenEmu ne s'en sert pas, et la carte renvoie au site plutôt que d'approcher ces valeurs. Ouverte depuis les paramètres, elle réutilise le jeton mémorisé pour demander les points au serveur, sur un client qui vit le temps du dialogue.

## Mémoire exposée

Les adresses suivent `src/rcheevos/consoleinfo.c` du SDK :

- GB/GBC : ROM bancarisée 0000–7FFF, VRAM banque 0, RAM cartouche physique banque 0, WRAM banques 0/1 fixes et miroir, OAM, HRAM, IE. GBC : banques WRAM 2–7 à 10000–15FFF ; RAM cartouche banques 1–15 à 16000–33FFF.
- GBA : IWRAM à 00000–07FFF, EWRAM à 08000–47FFF, les premiers 64 Kio de mémoire de sauvegarde à 48000–57FFF (limités à la taille réelle).

Les lectures consultent le stockage directement : aucun cycle CPU, effet de registre, commande Flash/EEPROM ou changement de banque. Les régions absentes, les registres GB et les mémoires spéciales non exposées renvoient une lecture incomplète. Le SDK peut désactiver les succès qui en dépendent ; l’intégration ne prétend pas prendre en charge ces conditions. Une cartouche MBC7 n’expose aucune mémoire de cartouche : ses sauvegardes vivent dans une EEPROM série et son espace 0xA000 répond à des registres, si bien que la lecture s’y arrête et se déclare incomplète plutôt que de rendre le tampon vierge que son en-tête fait allouer. Le MBC6 expose ses 32 Kio de SRAM, mais pas ses zones Flash.

## Vérification

```sh
cmake -S native -B build/achievements-host -DRAVENEMU_BUILD_ACHIEVEMENT_TESTS=ON
cmake --build build/achievements-host --target ravenemu_achievement_tests --parallel
build/achievements-host/achievements/ravenemu_achievement_tests
```

Les tests n’utilisent ni compte ni ROM commerciale : ROMs synthétiques, serveur simulé, reconnaissance par hash, chargement des conditions et déblocage réel par le SDK en réponse à une écriture CPU. Ils vérifient le mode Softcore, l’unicité du déblocage après reset, les banques CGB fixes, les limites et l’absence d’effets de bord des lectures mémoire, ainsi que la fermeture pendant login, chargement, démarrage de session, envoi et nouvelle tentative différée.

Avant validation sur appareil : connecter un compte de test, ouvrir une ROM reconnue, obtenir un succès, vérifier sa présence sur le site, essayer pause/reprise, restauration d’état, coupure puis retour du réseau, fermeture pendant une requête et déconnexion. Les envois Softcore journalisés persistent maintenant après fermeture ; la reprise sur réseau réel doit encore être validée. Aucun résultat de test synthétique ne valide à lui seul le service en production ou la compatibilité d’un jeu commercial.

Le [plan Hardcore](HARDCORE.md) décrit les protections et la validation restantes ; le mode reste désactivé.
