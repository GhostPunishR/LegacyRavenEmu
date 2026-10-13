# Site officiel RavenEmu

Site statique publié avec GitHub Pages depuis `main`, dossier `docs/`. Ouvrir `index.html` ou servir ce dossier avec un serveur HTTP local ; aucune dépendance ni compilation n'est nécessaire.

- `index.html` : présentation du projet `multiplateforme/`, état Android/iOS, accès à la bêta, documentation et FAQ.
- `assets/styles.css` : styles responsive, sans animation, transition ou défilement animé.
- `assets/app.js` : copie de la commande et année du pied de page. Navigation, contenu et FAQ fonctionnent sans JavaScript.

La bêta actuelle est l'artefact `ravenemu-android-beta` du workflow `multiplateforme.yml` (compte GitHub requis, rétention de 7 jours). L'APK `test-latest` appartient à la V2 et ne doit pas être présenté comme la version actuelle.

Avant une mise à jour, comparer les fonctions et les statuts à `multiplateforme/README.md`, au workflow et à `PRIVACY.md`. Ne pas présenter le lecteur iOS ou les validations RetroAchievements/GBC/GBA comme terminés avant confirmation.

Vérification rapide : `node --check docs/assets/app.js` et `git diff --check`. Contrôler aussi les ancres, les chemins locaux et le rendu desktop/mobile. Le site utilise uniquement ses assets locaux et n'ajoute aucun service externe ni outil de suivi.
