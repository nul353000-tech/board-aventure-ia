package fr.boardaventure.ia

object ActionPolicy {
    const val TARGET_PACKAGE = "com.moonactive.cmboard"
    const val MIN_CONFIDENCE = 0.93
    data class Decision(val allowed: Boolean, val explanation: String)

    fun validate(foregroundPackage: String?, recognized: Boolean, confidence: Double,
        action: String, x: Int?, y: Int?, screenWidth: Int, screenHeight: Int, turns: Long?): Decision {
        if (foregroundPackage != TARGET_PACKAGE) return Decision(false, "Application au premier plan différente du jeu cible.")
        if (!recognized || confidence < MIN_CONFIDENCE) return Decision(false, "Reconnaissance insuffisante.")
        if (action == "none") return Decision(false, "Aucune action demandée.")
        if (action !in setOf("spin", "collect_free_reward", "upgrade_village")) return Decision(false, "Action non autorisée.")
        if (x == null || y == null || x !in 1 until screenWidth || y !in 1 until screenHeight) return Decision(false, "Coordonnées absentes ou hors écran.")
        if (action == "spin" && (turns == null || turns <= 5L)) return Decision(false, "Réserve minimale de 5 tours non respectée ou compteur inconnu.")
        return Decision(false, "Zone d'action non calibrée : validation humaine requise.")
    }
}
