# Fournisseurs IA gratuits pour Board Aventure IA

L'application envoie une capture du jeu à un modèle multimodal et attend une réponse JSON stricte. Un fournisseur de secours doit donc accepter les images et les réponses de type chat completions.

## Fournisseurs intégrés

| Fournisseur | Modèle vision par défaut | Offre gratuite | Déclenchement du secours |
|---|---|---|---|
| Google Gemini | `gemini-2.5-flash-lite` | Quotas gratuits Google AI Studio, variables selon le modèle et le compte | HTTP 402/429, erreur d'accès, erreur réseau ou réponse inexploitable |
| Groq | `qwen/qwen3.8-27b` | Quotas gratuits du plan Free, variables selon le compte et le modèle | HTTP 402/429, modèle indisponible, erreur réseau ou réponse inexploitable |
| OpenRouter | Modèles marqués `:free` uniquement | Disponibilité et limites variables selon les modèles gratuits | Le routeur peut essayer ses modèles gratuits configurés, puis l'application passe au fournisseur suivant |

## Configuration

1. Créer une clé Google AI Studio : https://aistudio.google.com/app/apikey
2. Créer une clé Groq : https://console.groq.com/keys
3. Dans l'application, choisir le fournisseur préféré et son modèle.
4. Enregistrer les clés correspondantes. Les champs laissés vides conservent les clés déjà enregistrées.
5. Cocher les fournisseurs qui peuvent être utilisés en secours.
6. Enregistrer. Les clés sont chiffrées localement avec Android Keystore.

## Politique de coût

- Aucun modèle payant n'est proposé dans les sélections intégrées.
- OpenRouter est limité aux identifiants de modèle suffixés par `:free`; les anciens identifiants payants sont remplacés par le modèle gratuit de secours.
- Gemini et Groq utilisent les quotas gratuits des comptes correspondants. Les limites exactes sont définies par le fournisseur et peuvent évoluer.
- Pour garantir l'absence de facturation, ne pas activer la facturation sur les comptes API et vérifier les conditions du fournisseur avant usage.
- L'application ne peut pas lire le solde de crédits d'un fournisseur. Elle détecte les réponses HTTP d'épuisement/limitation (notamment 402 et 429) et essaie le fournisseur suivant.
- Une clé invalide, un modèle retiré, une erreur réseau ou une réponse IA invalide provoque également une tentative de secours; toutes les erreurs sont affichées si aucun fournisseur ne réussit.

## Sécurité et confidentialité

Les captures d'écran sont transmises au fournisseur sélectionné. Éviter d'afficher des informations privées dans le jeu ou les notifications avant l'analyse. Les clés sont chiffrées sur l'appareil, mais la protection locale ne change pas les règles de conservation et de traitement du fournisseur.

## Documentation officielle vérifiée

- Gemini API – tarification et niveaux gratuits : https://ai.google.dev/gemini-api/docs/pricing
- Gemini API – entrée image : https://ai.google.dev/gemini-api/docs/image-understanding
- Groq – modèles vision et entrée image : https://console.groq.com/docs/vision
- Groq – limites de débit et HTTP 429 : https://console.groq.com/docs/rate-limits
- Groq – modèles disponibles : https://console.groq.com/docs/models
- Android – clés dans Android Keystore : https://developer.android.com/privacy-and-security/keystore

Les quotas, noms de modèles et conditions d'accès peuvent changer. Revoir ces pages avant chaque mise à jour majeure.
