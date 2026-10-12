# RavenEmu V3

Aperçu de la future interface **identique sur Android et iOS**. Le projet vit dans `v3/` sur la branche `feature/v3-multiplatform` et ne remplace pas encore RavenEmu Android V2.

## État de la V3

- **Étape 2, interface fidèle à RavenEmu 0.8.1 :** palette sombre identique, barre compacte avec titre de console centré, recherche permanente, pages console balayables, grille de jaquettes sans cartes, mode liste, indicateur de pages et signature en bas. Les écrans sont construits une fois dans Compose Multiplatform pour Android et iOS.
- La bibliothèque affiche uniquement les consoles possédant des jeux, comme la V2. L'import Android `.gb` reste accessible par le bouton +. Les ROM importées sont maintenant conservées dans une bibliothèque privée persistante par empreinte SHA-256 ; leur fiche permet de les rouvrir sans sélectionner à nouveau le fichier d'origine.
- Les jaquettes peuvent être choisies manuellement depuis la fiche d'un jeu : JPG, PNG ou autre image acceptée par le sélecteur Android, copiée sous forme de JPEG redimensionné dans l'espace privé V3 sous la même empreinte SHA-256 que la ROM. Elles s'affichent dans la grille, la liste et la fiche, même après fermeture de l'application. Les jeux sans jaquette gardent un emplacement neutre ; aucune image commerciale n'est téléchargée automatiquement. L'écran Paramètres V3 est réparti en Général, Audio, Vidéo, Interface et À propos ; les skins Delta et RetroAchievements restent à migrer.
- Une passerelle C native reliant les moteurs maison GB/GBC/GBA, avec adaptateurs Android JNI et Swift.
- **Étape 1 :** import d'un fichier `.gb` depuis le sélecteur Android et chargement dans le vrai cœur RavenGB. L'écran confirme le nom et les dimensions de la ROM si le chargement réussit.
- La préversion initiale ne chargeait les fichiers qu'en mémoire ; une importation est encore nécessaire une fois pour les jeux ajoutés avec les anciens APK. Le dossier ROM privé disparaît lors de la désinstallation ou d'un effacement des données.
- Les fichiers vides, non `.gb`, invalides ou supérieurs à 16 Mio sont refusés. Un échec conserve la session précédente si elle existe.

**Étape 3, vidéo GB Android :** la fiche du jeu comporte "Afficher l'écran". Le lecteur exécute des frames RavenGB sur un thread de travail et affiche le vrai framebuffer sans lissage dans le lecteur Compose partagé. L'exécution est suspendue quand l'écran est fermé ou que l'application passe en arrière-plan.

**Diagnostic performance Android :** les moteurs C++ de la preview sont compilés avec `-O2` même quand l'APK Gradle est en Debug (le build iOS et les autres projets ne changent pas). Le tampon de pixels JNI est réutilisé d'une frame à l'autre. Une mesure sur une fenêtre d'environ une seconde montre les **frames calculées par seconde**, la cible du moteur et les temps moyens de calcul natif et conversion d'image. Ces nombres servent au diagnostic sur appareil réel : ils ne sont pas des FPS garantis à l'écran. Les images Bitmap restent allouées à chaque frame pour ne pas modifier un tampon pendant qu'il est affiché.

**Étape 4, commandes GB Android :** la croix directionnelle (glissement et diagonales), A, B, Start et Select appellent désormais le cœur natif. Les pressions sont sérialisées avec l'exécution des frames et sont relâchées quand le lecteur est fermé ou que l'application passe en arrière-plan. La prévisualisation est désormais interactive, mais sans audio ni sauvegardes persistantes. L'écran Compose applique les marges système sûres pour éviter que le nom du jeu ou les commandes soient masqués par les barres Android.

**Retour Android reçu :** commandes Game Boy fonctionnelles, barres système correctement espacées et image à environ 59,9 FPS calculés. Ajustement suivant : regroupement ergonomique de A/B et dimension de la croix adaptée à la largeur, Start/Select rapprochés et télémétrie FPS/moteur/conversion compacte juste sous le titre. Le viewport Game Boy reste à l'échelle avec pixels sans lissage, et les commandes restent en bas de la zone sûre.

**Étape 5, audio GB Android :** le cœur RavenGB fournit les échantillons stéréo PCM16 après chaque frame vidéo, et la sortie `AudioTrack` les joue avec une file bornée non bloquante, arrêt immédiat sur fermeture ou arrière-plan.

**Correctif audio après test utilisateur (pic sonore au lancement et musique dégradée) :** reprise du rééchantillonneur à bande limitée, du préremplissage d'environ six frames et du régulateur léger de dérive audio éprouvés dans RavenEmu 0.8.1. Sortie `AudioTrack` au débit préféré du téléphone, au lieu d'imposer celui du moteur, et démarrage adouci sur environ 30 ms. Les erreurs audio sont visibles sans bloquer la vidéo. Le résultat doit encore être validé à l'écoute sur Android et l'horloge reste provisoirement pilotée par la vidéo.

**Étape 6, sauvegardes de cartouche Game Boy sur Android (préversion) :** la RAM batterie produite par la fonction « Sauvegarder » du jeu est automatiquement enregistrée toutes les cinq secondes si elle a changé, ainsi qu'à la sortie du lecteur, au changement de ROM et à la mise en arrière-plan. L'écriture est atomique dans le stockage privé Android ; elle est identifiée par SHA-256 du contenu de la ROM et rechargée au prochain import du même fichier, même s'il a été renommé. La progression survit à la fermeture de l'application, mais pas à sa désinstallation ni à un effacement de ses données. Cela ne crée pas encore de bibliothèque persistante.

**Étape 7, save states GB Android (préversion) :** le menu ⋮ du lecteur propose « Sauvegarder l'état (emplacement 1) » et « Charger l'état (emplacement 1) ». Un état complet est conservé par ROM dans un fichier atomique distinct des sauvegardes batterie, identifiées par le SHA-256 de la ROM ; son rechargement reprend immédiatement la partie et renouvelle la sortie audio pour éviter les sons en retard. Un état corrompu, absent ou incompatible donne une erreur sans réinitialiser la partie. L'emplacement 1 est écrasé à chaque nouvelle sauvegarde manuelle. Les états dépendent de la version du cœur ; le support de plusieurs emplacements, des miniatures et de l'export viendra plus tard.

**Étape 8, réglages V3 (préversion) :** cinq parties dans l'interface Compose commune : navigation Paramètres, Audio, Vidéo, Interface, À propos. Sur Android, les préférences V3 sont persistantes et indépendantes de V2 : activation/volume de la sortie AudioTrack (fondu 180 ms inchangé), profil d'image Original/DMG vert/Pocket gris avec luminosité/contraste sur le framebuffer, affichage des mesures de performance, vibration des boutons, taille/écartement et style à contraste élevé. Les valeurs par défaut gardent les résultats validés sur téléphone. La catégorie Général explique la pause automatique ; elle ne permet pas de la désactiver. À propos donne version, auteur, code source et liens officiels vers les conditions, la confidentialité et la licence AGPL-3.0. Aucun réglage « Effacer l'index de la bibliothèque » n'est présent. Le gestionnaire Delta Skins, le déplacement libre des commandes et RetroAchievements ne sont pas présentés comme fonctionnels ; ils viendront ultérieurement. L'hôte iOS affiche les écrans partagés mais sa persistance et son lecteur natif restent à raccorder.

**Étape 9, bibliothèque persistante et commandes modernisées :** chaque ROM Game Boy correctement chargée est copiée atomiquement dans l'espace privé Android (une seule copie par SHA-256). Un petit index JSON atomique conserve ses métadonnées ; les jeux réapparaissent dans la grille/liste après un redémarrage, indépendamment du fichier d'origine, et « Jouer » dans leur fiche restaure le bon jeu. Revenir au même jeu déjà chargé en mémoire n'en réinitialise pas la progression. Les sauvegardes batterie et les save states restent associés au hash, et ne sont ni fusionnés ni supprimés. L'import d'une ROM déjà présente ne crée pas de doublon. Les fichiers ROM privés et l'index sont supprimés avec les données de l'application : ne pas désinstaller avant d'avoir exporté les sauvegardes. L'interface conserve le placement des boutons validé précédemment mais adopte des surfaces en relief, des contours plus lisibles et un retour visuel violet. Les actions des panneaux « Jouer », « Fermer » et « Reprendre le jeu » utilisent désormais de vrais boutons Material 3. Les skins personnalisés et les pochettes restent à développer.

**Étape 10, save states multi-emplacements et harmonisation visuelle :** le menu du lecteur contient quatre cartes d'emplacement par ROM, avec image PNG miniature du jeu et date/heure locale. Écraser une sauvegarde existante ou supprimer un emplacement demande une confirmation, sans jamais toucher à la SRAM batterie de la cartouche. L'emplacement 1 conserve exactement l'ancien chemin `<hash>.slot0.state` et est donc compatible avec les save states déjà créés ; une miniature absente ou illisible ne bloque pas le chargement. Les fichiers d'état et les images sont écrits atomiquement. Le lecteur reprend après un chargement, avec redémarrage propre de la sortie audio. La bibliothèque conserve sa navigation V2 (grille, liste, pagination par console), mais modernise la recherche, les pochettes provisoires et le bouton Paramètres ; les boutons + et ⋮ restent inchangés. L'écran Paramètres n'affiche plus l'en-tête introductif « RavenEmu V3 » ni son sous-texte et ses catégories adoptent un style plus soigné. V2 0.8.1 n'est pas modifiée.

**Étape 11, jaquettes personnalisées Android :** un jeu de la bibliothèque persistante peut recevoir une image au choix depuis sa fiche (bouton « Choisir une image »). Le fichier est décodé avec une limite de 8 Mio, redimensionné et enregistré atomiquement sous forme de JPEG compact dans `filesDir/gb-covers/<sha256>.jpg`. L'illustration apparaît dans la grille, la liste et la fiche après redémarrage. L'utilisateur peut retirer uniquement l'image sans affecter la ROM, la sauvegarde batterie, ni les quatre save states. Les fichiers d'origine restent privés et aucune jaquette n'est téléchargée à partir d'un catalogue tiers. Les pages de bibliothèque, icônes issues de V2, boutons + et ⋮ et la position des commandes ne sont pas modifiés. La prise en charge iOS des images est à brancher ultérieurement.

**Étape 12, jaquettes automatiques Game Boy :** au chargement de la bibliothèque et après importation d'une ROM, V3 peut consulter le catalogue public `Nintendo - Game Boy/Named_Boxarts` de `thumbnails.libretro.com` (Libretro). La recherche rapproche le nom de fichier et, lorsqu'elle est renseignée, la région (par exemple `Pokemon - Version Bleue (France).gb` → jaquette française). Seuls l'index public et l'image correspondante sont téléchargés par HTTPS sur un thread IO, avec délais et tailles bornés ; aucun contenu de ROM, SRAM, save state ni empreinte privée n'est envoyé. L'image est enregistrée sous le hash ROM par le même stockage JPEG privé que les couvertures choisies manuellement. Une image déjà sélectionnée est conservée ; l'utilisateur peut toujours choisir sa propre image si aucune correspondance sûre n'est trouvée. Retirer une jaquette désactive son retéléchargement automatique pour ce jeu. Une absence de réseau ou de correspondance conserve le placeholder GB, sans bloquer la bibliothèque ni l'émulation. L'autorisation Android `INTERNET` ne s'applique qu'à V3 ; le catalogue iOS sera adapté séparément.

**Étape 13, transfert des sauvegardes Android :** depuis la fiche d'un jeu, « Exporter toutes les sauvegardes (.zip) » crée un paquet contenant la sauvegarde SRAM `.sav`, les save states 1 à 4 occupés et leurs miniatures facultatives. Le paquet a un manifeste versionné avec l'empreinte SHA-256 de la ROM (sans contenir sa ROM) et les empreintes de chaque fichier. L'option « Exporter .sav » fournit un fichier batterie brut pour la compatibilité avec d'autres émulateurs. « Importer » accepte soit un ZIP RavenEmu V3 pour la **même ROM exacte**, soit un fichier `.sav` brut, qui ne peut pas être vérifié automatiquement : un avertissement apparaît et l'utilisateur confirme le jeu cible. Les fichiers sont contrôlés (noms permis, tailles bornées, sommes de contrôle, version, absence de chemins traversants) avant toute écriture. Le résumé de confirmation précise les fichiers existants qui seront écrasés ; les fichiers absents de l'import restent intacts, à l'exception d'une ancienne miniature retirée si son save state est remplacé sans nouvelle miniature. Une tentative d'import sur le jeu encore chargé libère d'abord le cœur et prévient ainsi qu'une RAM interne obsolète écrase le nouveau `.sav`. Les écritures sont atomiques par fichier, avec copie/rollback des valeurs précédentes en cas d'erreur ; aucun import ne modifie les ROM. L'accès aux fichiers se fait via le sélecteur Documents Android sans permission d'accès général au stockage. Ne pas désinstaller avant de transférer les sauvegardes ; la version iOS n'est pas encore connectée à ce transfert.

**Encore à réaliser :** validation sur téléphone des exports ZIP et SAV, réimportation avec confirmations, essais sur un second téléphone, test complet des quatre save states, détection des dossiers ROM, skins Delta, import et lecteur iOS, autres consoles et RetroAchievements.

## Architecture

```text
v3/
  shared/       UI Compose et logique Kotlin Multiplatform
  androidApp/   Activité Android et packaging de prévisualisation
  iosApp/       Hôte iOS SwiftUI + spécification XcodeGen
```

Le code d'émulation reste dans [RavenEmu-Cores](https://github.com/GhostPunishR/RavenEmu-Cores). Une API C de préversion existe désormais pour les moteurs C++20, utilisée côté Android par JNI et côté iOS par Swift. **Libretro n'est pas l'interface native de l'application.**

## Android

Prérequis : JDK 21, Android SDK avec API 37, Gradle Wrapper du dépôt.

À partir de la racine de RavenEmu :

```sh
./gradlew -p v3 :androidApp:assembleDebug
```

APK : `v3/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

Pour exécuter les tests Kotlin communs, utilisez les tâches de test disponibles sur votre plateforme et `./gradlew -p v3 :shared:tasks`.

## iOS (sur macOS)

Prérequis : Xcode, les outils de compilation iOS, JDK 21 et [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```sh
cd v3/iosApp
xcodegen generate
open RavenEmuV3.xcodeproj
```

Sélectionnez le simulateur iOS et démarrez l'application depuis Xcode. Le script de construction de l'hôte compile et intègre `RavenShared` via le Gradle Wrapper du dépôt. Pour tester seulement le framework :

```sh
./gradlew -p v3 :shared:linkDebugFrameworkIosSimulatorArm64
```

L'application iOS et la compilation Apple nécessitent un Mac avec Xcode. Aucun binaire iOS signé n'est encore distribué.

## Passerelle native GB / GBC / GBA

La base native est disponible dans [`native/`](native/README.md). Elle relie les trois cœurs maison depuis le commit épinglé [9f5d4d9 de RavenEmu-Cores 0.1.0](https://github.com/GhostPunishR/RavenEmu-Cores/commit/9f5d4d99e40054f84b0e74efa04135edd1ef9288). Les PR #1 à #8 apportent contrôles de sécurité, améliorations d'API, correctifs de portabilité et optimisations des sauvegardes d'état. Le mixeur audio GB dans `apu.hpp` est inchangé ; cette mise à jour n'est pas un correctif du son GB.

- **Android** : `NativeCore.kt` charge la bibliothèque JNI `ravenemu_v3_android`. Elle permet de charger des octets de ROM et de lire les frames ARGB8888, l'audio stéréo et les boutons.
- **iOS** : `RavenNativeCore.swift` importe la même API C compilée pour l'architecture Apple. Le script `sh v3/native/build-ios.sh` produit les archives C++ nécessaires.

L'import GB Android charge maintenant les ROMs dans le cœur natif. L'exécution des frames, les commandes tactiles et le flux audio PCM16 sont raccordés dans le lecteur Android de préversion. Une compilation native réussie n'atteste pas encore de l'exécution sur un appareil iOS.

## Isolation et suite

Les fichiers Gradle V2 à la racine ne changent pas ; V3 possède son propre `settings.gradle.kts`. Une modification de l'application V2 ou des cœurs ne met pas automatiquement V3 à jour.

Prochaines étapes, dans cet ordre : valider le save state GB sur Android (enregistrement et chargement, y compris après redémarrage, audio/vidéo corrects), puis plusieurs emplacements et l'export des sauvegardes, puis importer et jouer sur iOS, enfin GBC/GBA. L'interface doit rester la même sur les deux plateformes, avec adaptation aux tailles d'écran.
