# Board Aventure IA — V0.3 sécurisation

Projet Android Kotlin de capture et analyse visuelle du jeu Board Aventure.

- Capture écran via AccessibilityService.
- Analyse d'image avec OpenRouter Vision.
- Clé OpenRouter chiffrée avec Android Keystore.
- Lecture structurée des tours, tours bonus, pièces, gemmes, étoiles et multiplicateur.
- Réserve stratégique de 5 tours ; les clics autonomes et dépenses sont désactivés.
- Cible : package com.moonactive.cmboard.

## Compilation
Le workflow GitHub Actions à la racine compile l'APK debug et publie l'artefact `Board-Aventure-IA-debug-apk`.

## Configuration
1. Installer l'APK depuis l'artefact GitHub Actions.
2. Activer le service d'accessibilité dans les paramètres Android.
3. Entrer la clé OpenRouter et un modèle vision disponible.
4. Ouvrir Board Aventure et lancer l'analyse depuis l'application.

## Limites
Ce projet source n'a pas encore été compilé ni testé sur Android 17 / Pixel 10a. Les sept images de référence ne sont pas intégrées. L'automatisation des clics n'est pas activée tant que les zones tactiles réelles ne sont pas calibrées ; aucun achat en argent réel n'est effectué.


## Installation Android 13+ (autorisation système unique)
Android peut bloquer l'activation d'un service d'accessibilité pour une application installée depuis un APK externe. Cette protection ne peut pas être désactivée par l'application.
1. Ouvrir Paramètres Android > Applications > Board Aventure IA.
2. Ouvrir le menu ⋮ puis choisir « Autoriser les paramètres restreints » si Android le propose.
3. Revenir dans Paramètres > Accessibilité > Applications/services installés > Assistant Board Aventure, puis l'activer.
4. Cette autorisation système peut être demandée à nouveau après une désinstallation/réinstallation ou une nouvelle installation considérée comme provenant d'une source différente.

Une fois le service activé, ouvrir le jeu cible `com.moonactive.cmboard`. Le bouton flottant « Analyser » n'apparaît que lorsque le jeu est au premier plan. L'analyse affiche les valeurs et le conseil via une notification Toast ; elle n'exécute aucun clic ni aucune dépense.
