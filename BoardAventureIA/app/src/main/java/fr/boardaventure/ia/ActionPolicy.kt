package fr.boardaventure.ia

object ActionPolicy {
    const val TARGET_PACKAGE = "com.moonactive.cmboard"
    const val MIN_CONFIDENCE = 0.93

    data class Decision(val allowed: Boolean, val explanation: String)

    enum class ZoneId { SPIN, MULTIPLIER, FREE_REWARD, VILLAGE_UPGRADE }

    data class Hitbox(
        val zone: ZoneId,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val verifiedForDevice: Boolean
    ) {
        fun isValid(): Boolean =
            left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
                left >= 0f && top >= 0f && right <= 1f && bottom <= 1f &&
                left < right && top < bottom
    }

    // Price-strip centers/bounds measured from the clean 684x1536 village screenshot.
    // Preview-only: never used as click authorization.
    data class CardTarget(
        val number: Int, val centerX: Float, val centerY: Float,
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val verifiedForDevice: Boolean = false
    )
    private val villageCardTargets = listOf(
        CardTarget(1, 72f/684f, 1487f/1536f, 7f/684f, 1446f/1536f, 137f/684f, 1527f/1536f),
        CardTarget(2, 208f/684f, 1487f/1536f, 143f/684f, 1446f/1536f, 274f/684f, 1527f/1536f),
        CardTarget(3, 344f/684f, 1487f/1536f, 278f/684f, 1446f/1536f, 410f/684f, 1527f/1536f),
        CardTarget(4, 480f/684f, 1487f/1536f, 414f/684f, 1446f/1536f, 547f/684f, 1527f/1536f),
        CardTarget(5, 616f/684f, 1487f/1536f, 551f/684f, 1446f/1536f, 682f/684f, 1527f/1536f)
    )
    // Main board controls measured from the 684x1536 gameplay screenshot.
    // Dry-run coordinates only; never authorize a tap.
    data class ControlTarget(
        val name: String, val centerX: Float, val centerY: Float,
        val left: Float, val top: Float, val right: Float, val bottom: Float
    )
    private val spinTarget = ControlTarget(
        "SPIN", 344f/684f, 1310f/1536f,
        220f/684f, 1208f/1536f, 466f/684f, 1415f/1536f
    )
    private val multiplierTarget = ControlTarget(
        "MULTIPLIER", 535f/684f, 1335f/1536f,
        465f/684f, 1285f/1536f, 609f/684f, 1385f/1536f
    )
    private val calibratedHitboxes: Map<ZoneId, Hitbox> = emptyMap()

    fun controlPreview(name: String, screenWidth: Int, screenHeight: Int): Pair<Int, Int>? {
        if (screenWidth <= 1 || screenHeight <= 1) return null
        if (kotlin.math.abs(screenWidth.toFloat() / screenHeight - 684f / 1536f) > 0.015f) return null
        val target = when (name.lowercase()) {
            "spin" -> spinTarget
            "multiplier" -> multiplierTarget
            else -> return null
        }
        return (target.centerX * screenWidth).toInt() to (target.centerY * screenHeight).toInt()
    }

    fun villageCardPreview(number:Int,screenWidth:Int,screenHeight:Int):Pair<Int,Int>? {
        if(screenWidth<=1||screenHeight<=1) return null
        if(kotlin.math.abs(screenWidth.toFloat()/screenHeight-684f/1536f)>0.015f) return null
        val t=villageCardTargets.firstOrNull{it.number==number}?:return null
        return (t.centerX*screenWidth).toInt() to (t.centerY*screenHeight).toInt()
    }
    fun villageCardCalibrationStatus():String = "5 cartes, bouton spin et sélecteur multiplicateur repérés sur captures; simulation uniquement, validation tactile réelle requise."
    fun calibrationStatus(): String {
        val missing = ZoneId.values().filterNot { zone ->
            calibratedHitboxes[zone]?.let { it.isValid() && it.verifiedForDevice } == true
        }
        return if (missing.isEmpty()) "Toutes les zones sont vérifiées."
        else "Zones à vérifier : " + missing.joinToString { it.name }
    }

    fun validate(
        foregroundPackage: String?,
        recognized: Boolean,
        confidence: Double,
        action: String,
        x: Int?,
        y: Int?,
        screenWidth: Int,
        screenHeight: Int
    ): Decision {
        if (foregroundPackage != TARGET_PACKAGE) {
            return Decision(false, "Application au premier plan différente du jeu cible.")
        }
        if (!recognized || !confidence.isFinite() || confidence !in MIN_CONFIDENCE..1.0) {
            return Decision(false, "Reconnaissance insuffisante ou score de confiance invalide.")
        }
        val zone = when (action) {
            "spin" -> ZoneId.SPIN
            "collect_free_reward" -> ZoneId.FREE_REWARD
            "upgrade_village" -> ZoneId.VILLAGE_UPGRADE
            "none" -> return Decision(false, "Aucune action demandée.")
            else -> return Decision(false, "Action non autorisée.")
        }
        if (screenWidth <= 1 || screenHeight <= 1 || x == null || y == null ||
            x !in 0 until screenWidth || y !in 0 until screenHeight) {
            return Decision(false, "Coordonnées absentes, dimensions invalides ou coordonnées hors écran.")
        }
        val hitbox = calibratedHitboxes[zone]
            ?: return Decision(false, "Zone ${zone.name} non calibrée sur cet appareil.")
        if (!hitbox.isValid() || !hitbox.verifiedForDevice) {
            return Decision(false, "Zone ${zone.name} non vérifiée sur cet appareil.")
        }
        val nx = x.toFloat() / screenWidth
        val ny = y.toFloat() / screenHeight
        if (nx !in hitbox.left..hitbox.right || ny !in hitbox.top..hitbox.bottom) {
            return Decision(false, "Coordonnées hors de la zone calibrée ${zone.name}.")
        }
        return Decision(false, "Validation tactile réelle encore requise avant activation.")
    }
}
