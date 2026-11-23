package fr.boardaventure.ia

object ActionPolicy {
    const val TARGET_PACKAGE = "com.moonactive.cmboard"
    const val MIN_CONFIDENCE = 0.93
    data class Decision(val allowed: Boolean, val explanation: String)
    private val allowedActions = setOf("spin", "collect_free_reward", "upgrade_village")

    fun validate(
        foregroundPackage: String?, recognized: Boolean, confidence: Double, action: String,
        x: Int?, y: Int?, screenWidth: Int, screenHeight: Int, turns: Long?
    ): Decision {
        if (foregroundPackage != TARGET_PACKAGE) return Decision(false, "Application au premier plan différente du jeu cible.")
        if (!recognized || !confidence.isFinite() || confidence !in MIN_CONFIDENCE..1.0) {
            return Decision(false, "Reconnaissance insuffisante ou score de confiance invalide.")
        }
        if (action == "none") return Decision(false, "Aucune action demandée.")
        if (action !in allowedActions) return Decision(false, "Action non autorisée.")
        if (screenWidth <= 1 || screenHeight <= 1 || x == null || y == null ||
            x !in 1 until screenWidth || y !in 1 until screenHeight) {
            return Decision(false, "Coordonnées absentes, dimensions invalides ou coordonnées hors écran.")
        }
        if (action == "spin" && (turns == null || turns <= 5L)) {
            return Decision(false, "Réserve minimale de 5 tours non respectée ou compteur inconnu.")
        }
        // Safety lock: game hitboxes are not calibrated on a real device yet.
        return Decision(false, "Zone d'action non calibrée : validation humaine requise.")
    }
}