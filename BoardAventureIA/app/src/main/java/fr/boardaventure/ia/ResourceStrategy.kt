package fr.boardaventure.ia

/**
 * Conservative resource planner. It computes recommendations only.
 * It never dispatches gestures or spends game resources.
 */
object ResourceStrategy {
    const val DEFAULT_ENERGY_RESERVE = 20L
    const val DEFAULT_BONUS_RESERVE = 5L
    const val DEFAULT_COIN_RESERVE_PERCENT = 10L
    const val DEFAULT_MULTIPLIER = 1

    enum class SpinCommand { TAP_SINGLE, HOLD_TO_START_AUTO, TAP_STOP, WAIT, BLOCKED }

    data class Plan(
        val spinCommand: SpinCommand,
        val multiplier: Int?,
        val recommendedMultiplier: Int,
        val affordableCards: List<Int>,
        val suggestedCard: Int?,
        val coinReserve: Long?,
        val explanation: String
    )

    fun isProgressGoalReached(value: String?): Boolean {
        val match = Regex("""(\d{1,4})\s*/\s*(\d{1,4})""").find(value.orEmpty()) ?: return false
        val current = match.groupValues[1].toIntOrNull() ?: return false
        val target = match.groupValues[2].toIntOrNull() ?: return false
        return current >= 725 && target == 725
    }

    fun plan(
        recognized: Boolean,
        confidence: Double,
        coinsText: String?,
        energyText: String?,
        energyBonusText: String?,
        multiplierText: String?,
        spinState: String?,
        villageCostsText: String?,
        energyReserve: Long = DEFAULT_ENERGY_RESERVE,
        bonusReserve: Long = DEFAULT_BONUS_RESERVE,
        coinReservePercent: Long = DEFAULT_COIN_RESERVE_PERCENT
    ): Plan {
        if (!recognized || !confidence.isFinite() || confidence < ActionPolicy.MIN_CONFIDENCE || confidence > 1.0) {
            return blocked("Jeu ou lecture non fiable.")
        }

        val energy = parseEnergy(energyText)
        val bonus = parseLong(energyBonusText)
        val currentEnergy = energy?.first
        val totalEnergy = if (currentEnergy != null && bonus != null) currentEnergy + bonus else null
        val multiplier = parseMultiplier(multiplierText)
        val coins = parseLong(coinsText)
        val reserve = coins?.let { it * coinReservePercent.coerceIn(0L, 30L) / 100L }
        val costs = parseCosts(villageCostsText)
        val affordable = if (coins != null && reserve != null) {
            costs.mapIndexedNotNull { index, cost ->
                if (cost != null && cost >= 0 && cost <= coins - reserve) index + 1 else null
            }
        } else emptyList()
        // The game requires every building to be completed. Without reliable per-building
        // level/damage/event data, suggest only the first visible affordable card for review;
        // never infer that this single purchase completes the village.
        val suggested = affordable.firstOrNull()

        val command = when (spinState?.lowercase()) {
            "auto_active" -> SpinCommand.TAP_STOP
            "ready" -> when {
                currentEnergy == null || bonus == null -> SpinCommand.BLOCKED
                currentEnergy <= energyReserve.coerceIn(0L, 100L) && bonus > 0L -> SpinCommand.TAP_SINGLE
                currentEnergy <= DEFAULT_ENERGY_RESERVE -> SpinCommand.WAIT
                bonus > bonusReserve.coerceIn(0L, 50L) -> SpinCommand.TAP_SINGLE
                currentEnergy < energyReserve.coerceIn(0L, 100L) + 20L -> SpinCommand.TAP_SINGLE
                else -> SpinCommand.WAIT
            }
            else -> SpinCommand.BLOCKED
        }

        val explanation = buildString {
            append("Énergie normale: ")
            append(currentEnergy?.toString() ?: "inconnue")
            append(" · énergie supplémentaire: ")
            append(bonus?.toString() ?: "inconnue")
            append(" · total disponible: ")
            append(totalEnergy?.toString() ?: "inconnu")
            append(" · réserve normale: ")
            append(energyReserve.coerceIn(0L, 100L))
            append(" · réserve tours bonus: ")
            append(bonusReserve.coerceIn(0L, 50L))
            append(". Multiplicateur affiché: x")
            append(multiplier ?: "illisible")
            append(". Multiplicateur recommandé: x")
            append(DEFAULT_MULTIPLIER)
            append(" (choix prudent : rendement par énergie non mesuré).")
            append(". Réserve pièces: ")
            append(reserve?.toString() ?: "inconnue")
            append(". ")
            append(if (suggested == null) "Aucune carte achetable avec la réserve." else "Carte village conseillée: $suggested.")
            append(" Réserve protégée: 5 tours bonus. AUTO suspendu tant que le multiplicateur n’est pas confirmé par une lecture fiable.")
            append(" Les achats, gemmes et paiements restent interdits.")
        }

        return Plan(command, multiplier, DEFAULT_MULTIPLIER, affordable, suggested, reserve, explanation)
    }

    private fun blocked(reason: String) = Plan(
        SpinCommand.BLOCKED, null, DEFAULT_MULTIPLIER, emptyList(), null, null,
        "$reason Toutes les actions restent bloquées."
    )

    private fun parseLong(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val digits = value.filter { it.isDigit() }
        return digits.toLongOrNull()
    }

    private fun parseEnergy(value: String?): Pair<Long, Long>? {
        if (value.isNullOrBlank()) return null
        val parts = value.split("/", limit = 2)
        if (parts.size != 2) return null
        val current = parseLong(parts[0]) ?: return null
        val maximum = parseLong(parts[1]) ?: return null
        if (maximum <= 0 || current > maximum) return null
        return current to maximum
    }

    private fun parseMultiplier(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val digits = value.filter { it.isDigit() }
        return digits.toIntOrNull()?.takeIf { it in 1..20_000 }
    }

    private fun parseCosts(value: String?): List<Long?> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").map { token ->
            if (token.trim() == "-1") null else parseLong(token)
        }
    }
}
