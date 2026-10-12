# RavenEmu V3

Aperçu de la future interface **identique sur Android et iOS**. Le projet vit dans `v3/` sur la branche `feature/v3-multiplatform` et ne remplace pas encore RavenEmu Android V2.

## État de la V3

- **Étape 2, interface fidèle à RavenEmu 0.8.1 :** palette sombre identique, barre compacte avec titre de console centré, recherche permanente, pages console balayables, grille de jaquettes sans cartes, mode liste, indicateur de pages et signature en bas. Les écrans sont construits une fois dans Compose Multiplatform pour Android et iOS.
- La bibliothèque présente uniquement les consoles réellement chargées, comme la V2. L'import Android `.gb` reste accessible par le bouton +.
- Les jaquettes sont pour l'instant représentées par un espace réservé neutre : aucune fausse pochette ni jeu préchargé. Les réglages et le menu de jeu affichent clairement les fonctions non encore migrées.
- Une passerelle C native reliant les moteurs maison GB/GBC/GBA, avec adaptateurs Android JNI et Swift.
- **Étape 1 :** import d'un fichier `.gb` depuis le sélecteur Android et chargement dans le vrai cœur RavenGB. L'écran confirme le nom et les dimensions de la ROM si le chargement réussit.
- Le fichier n'est chargé **qu'en mémoire**. Il n'y a pas encore de bibliothèque persistante.
- Les fichiers vides, non `.gb`, invalides ou supérieurs à 16 Mio sont refusés. Un échec conserve la session précédente si elle existe.

**Étape 3, vidéo GB Android :** la fiche du jeu comporte "Afficher l'écran". Le lecteur exécute des frames RavenGB sur un thread de travail et affiche le vrai framebuffer sans lissage dans le lecteur Compose partagé. L'exécution est suspendue quand l'écran est fermé ou que l'application passe en arrière-plan.

**Diagnostic performance Android :** les moteurs C++ de la preview sont compilés avec `-O2` même quand l'APK Gradle est en Debug (le build iOS et les autres projets ne changent pas). Le tampon de pixels JNI est réutilisé d'une frame à l'autre. Une mesure sur une fenêtre d'environ une seconde montre les **frames calculées par seconde**, la cible du moteur et les temps moyens de calcul natif et conversion d'image. Ces nombres servent au diagnostic sur appareil réel : ils ne sont pas des FPS garantis à l'écran. Les images Bitmap restent allouées à chaque frame pour ne pas modifier un tampon pendant qu'il est affiché.

**Étape 4, commandes GB Android :** la croix directionnelle (glissement et diagonales), A, B, Start et Select appellent désormais le cœur natif. Les pressions sont sérialisées avec l'exécution des frames et sont relâchées quand le lecteur est fermé ou que l'application passe en arrière-plan. La prévisualisation est désormais interactive, mais sans audio ni sauvegardes persistantes. L'écran Compose applique les marges système sûres pour éviter que le nom du jeu ou les commandes soient masqués par les barres Android.

**Retour Android reçu :** commandes Game Boy fonctionnelles, barres système correctement espacées et image à environ 59,9 FPS calculés. Ajustement suivant : regroupement ergonomique de A/B et dimension de la croix adaptée à la largeur, Start/Select rapprochés et télémétrie FPS/moteur/conversion compacte juste sous le titre. Le viewport Game Boy reste à l'échelle avec pixels sans lissage, et les commandes restent en bas de la zone sûre.

**Étape 5, audio GB Android :** le cœur RavenGB fournit maintenant les échantillons stéréo PCM16 au même rythme que les frames vidéo. La sortie Android `AudioTrack` lit le flux à la fréquence du cœur via une file FIFO bornée et des écritures non bloquantes. L'audio démarre après un léger préremplissage et se coupe immédiatement à la fermeture du lecteur ou à la mise en arrière-plan. Une erreur du périphérique est affichée sans interrompre la vidéo. Le débit audio natif est utilisé; Android se charge pour l'instant du rééchantillonnage matériel. La stabilité/latence de l'audio reste à vérifier sur un vrai téléphone et peut demander ensuite une régulation de dérive.

**Encore à réaliser :** test audio Android (Pokémon Bleu : musique, absence de craquements, sortie/reprise et maintien d'environ 60 FPS), sauvegardes disque, véritables jaquettes et scan des dossiers, écran de réglages complet, import et lecteur iOS, autres consoles et RetroAchievements.

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

La base native est disponible dans [`native/`](native/README.md). Elle relie les trois cœurs maison depuis un commit épinglé de [RavenEmu-Cores](https://github.com/GhostPunishR/RavenEmu-Cores/pull/3).

- **Android** : `NativeCore.kt` charge la bibliothèque JNI `ravenemu_v3_android`. Elle permet de charger des octets de ROM et de lire les frames ARGB8888, l'audio stéréo et les boutons.
- **iOS** : `RavenNativeCore.swift` importe la même API C compilée pour l'architecture Apple. Le script `sh v3/native/build-ios.sh` produit les archives C++ nécessaires.

L'import GB Android charge maintenant les ROMs dans le cœur natif. L'exécution des frames, les commandes tactiles et le flux audio PCM16 sont raccordés dans le lecteur Android de préversion. Une compilation native réussie n'atteste pas encore de l'exécution sur un appareil iOS.

## Isolation et suite

Les fichiers Gradle V2 à la racine ne changent pas ; V3 possède son propre `settings.gradle.kts`. Une modification de l'application V2 ou des cœurs ne met pas automatiquement V3 à jour.

Prochaines étapes, dans cet ordre : valider l'audio GB sur téléphone Android sans perte de FPS ni craquements, puis les sauvegardes batterie et save states persistants, puis importer et jouer sur iOS, enfin GBC/GBA. L'interface doit rester la même sur les deux plateformes, avec adaptation aux tailles d'écran.
