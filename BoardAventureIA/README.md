# Board Aventure IA — audit et stabilisation

Application Android Kotlin destinée exclusivement au jeu Board Aventure (package Android cible : `com.moonactive.cmboard`).

## Fonctionnalités présentes dans le code
- Service Android AccessibilityService pour détection de l'application au premier plan.
- Panneau flottant d'analyse.
- Capture d'écran et envoi d'image à OpenRouter Vision.
- Clé OpenRouter chiffrée avec Android Keystore.
- Champs de lecture : tours, tours bonus, pièces, gemmes, étoiles et multiplicateur.
- Réserve stratégique de 5 tours documentée dans le prompt.
- Les clics et dépenses automatiques sont désactivés.

## Installation et première activation
1. Installer l'APK depuis l'artefact GitHub Actions.
2. Si Android affiche « Autoriser les paramètres restreints », l'autoriser manuellement dans les paramètres de l'application.
3. Activer « Assistant Board Aventure » dans Paramètres > Accessibilité.
4. Ouvrir Board Aventure : le panneau d'analyse doit apparaître sur le jeu.
5. Saisir la clé OpenRouter et un identifiant de modèle compatible avec l'entrée image. Pour un test reproductible, utiliser un modèle vision précis plutôt qu'un routeur aléatoire `openrouter/free`.

## Compilation et signature
Le workflow GitHub Actions construit l'APK debug, crée une clé de signature persistante si elle n'existe pas, la met en cache et vérifie la signature et l'identifiant de paquet avant publication. Le workflow contrôle l'empreinte SHA-256 du certificat attendu et bloque la publication si elle change. Ne supprime pas le cache de signature et ne change pas sa clé sans procédure de migration.

## Limites et tests à réaliser
- Une compilation réussie prouve que le code compile, pas que le panneau, la capture ou l'OCR fonctionnent sur le téléphone réel.
- Les sept images de référence n'ont pas été ajoutées au dépôt ; la reconnaissance n'est donc pas calibrée sur les écrans réels.
- Les compteurs ne sont pas encore validés par comparaison sur plusieurs captures réelles.
- Le code de politique d'action refuse actuellement toutes les actions ; aucun clic automatique n'est implémenté.
- Android peut exiger une autorisation manuelle des paramètres restreints pour activer un service d'accessibilité installé depuis un APK externe. L'application ne peut pas supprimer cette protection.
- Une désinstallation efface les préférences locales et peut rendre la clé OpenRouter à ressaisir.
- Le comportement doit encore être testé sur Android 17 / Pixel 10a. La compilation CI ne remplace pas ce test matériel.

## Sécurité
Aucun achat en argent réel ni aucune dépense automatique ne doit être exécuté. Les coordonnées tactiles ne doivent pas être activées avant calibration et validation humaine sur les écrans réels.
