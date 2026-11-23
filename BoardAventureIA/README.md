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
