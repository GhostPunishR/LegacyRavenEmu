# Site officiel RavenEmu

Site statique publié avec GitHub Pages depuis `main`, dossier `docs/`. Aucune dépendance ni compilation : ouvrir `index.html` ou servir ce dossier avec un serveur HTTP local.

Direction visuelle : sombre minimaliste, composition centrée sur le produit, violet discret. Pas d'animation, de transition ou de défilement animé. Le visuel de console de l'ancien site n'est plus utilisé. L'aperçu de bibliothèque est réalisé en HTML/CSS et explicitement présenté comme conceptuel ; les jeux et visuels sont des exemples illustratifs, pas une capture de l'application ni du contenu fourni.

- `index.html` : expérience Android, état des plateformes, installation de la bêta, ressources et FAQ.
- `assets/styles.css` : composition et styles responsive.
- `assets/app.js` : copie de la commande et année. Navigation, contenu et FAQ fonctionnent sans JavaScript.

La bêta actuelle est l'artefact `ravenemu-android-beta` de `multiplateforme.yml` (compte GitHub requis, rétention 7 jours). `test-latest` est l'ancien APK V2, plus mis à jour.

Sources de vérité : `multiplateforme/README.md`, le workflow et `PRIVACY.md`. Ne pas présenter iOS jouable ou les validations RetroAchievements/GBC/GBA comme terminés sans confirmation.

Vérification : `node --check docs/assets/app.js`, `git diff --check`, ancres, assets et chemins GitHub ; vérifier aussi le rendu desktop/mobile, le focus clavier et les débordements dans un navigateur. Le site utilise uniquement ses assets locaux et aucun outil de suivi.
