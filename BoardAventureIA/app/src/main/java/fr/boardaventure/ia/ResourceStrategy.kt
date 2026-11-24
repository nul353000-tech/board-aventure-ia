package fr.boardaventure.ia

/**
 * Conservative resource planner. It computes recommendations only.
 * It never dispatches gestures or spends game resources.
 */
object ResourceStrategy {
    const val DEFAULT_ENERGY_RESERVE = 20L
    const val DEFAULT_COIN_RESERVE_PERCENT = 10L
    const val DEFAULT_MULTIPLIER = 1

    enum class SpinCommand { HOLD_TO_START_AUTO, TAP_STOP, WAIT, BLOCKED }

    data class Plan(
        val spinCommand: SpinCommand,
        val multiplier: Int?,
        val affordableCards: List<Int>,
        val suggestedCard: Int?,
        val coinReserve: Long?,
        val explanation: String
    )

    fun plan(
        recognized: Boolean,
        confidence: Double,
        coinsText: String?,
        energyText: String?,
        energyBonusText: String?,
        multiplierText: String?,
        spinState: String?,
        villageCostsText: String?
    ): Plan {
        if (!recognized || !confidence.isFinite() || confidence < ActionPolicy.MIN_CONFIDENCE || confidence > 1.0) {
            return blocked("Jeu ou lecture non fiable.")
        }

        val energy = parseEnergy(energyText)
        val bonus = parseLong(energyBonusText)
        val totalEnergy = if (energy != null && bonus != null) energy.first + bonus else null
        val multiplier = parseMultiplier(multiplierText)
        val coins = parseLong(coinsText)
        val reserve = coins?.let { it * DEFAULT_COIN_RESERVE_PERCENT / 100L }
        val costs = parseCosts(villageCostsText)
        val affordable = if (coins != null && reserve != null) {
            costs.mapIndexedNotNull { index, cost ->
                if (cost != null && cost >= 0 && cost <= coins - reserve) index + 1 else null
            }
        } else emptyList()
        // Keep the largest remaining balance: among visible affordable cards,
        // suggest the cheapest one. No assumption is made about hidden rewards.
        val suggested = affordable.minByOrNull { costs[it - 1] ?: Long.MAX_VALUE }

        val command = when (spinState?.lowercase()) {
            "auto_active" -> when {
                totalEnergy == null -> SpinCommand.BLOCKED
                totalEnergy <= DEFAULT_ENERGY_RESERVE -> SpinCommand.TAP_STOP
                else -> SpinCommand.WAIT
            }
            "ready" -> when {
                totalEnergy == null -> SpinCommand.BLOCKED
                totalEnergy <= DEFAULT_ENERGY_RESERVE -> SpinCommand.WAIT
                else -> SpinCommand.HOLD_TO_START_AUTO
            }
            else -> SpinCommand.BLOCKED
        }

        val explanation = buildString {
            append("Réserve énergie: ")
            append(totalEnergy?.toString() ?: "inconnue")
            append("/ seuil ")
            append(DEFAULT_ENERGY_RESERVE)
            append(". Multiplicateur conseillé: x")
            append(multiplier ?: DEFAULT_MULTIPLIER)
            append(". Réserve pièces: ")
            append(reserve?.toString() ?: "inconnue")
            append(". ")
            append(if (suggested == null) "Aucune carte achetable avec la réserve." else "Carte village conseillée: $suggested.")
            append(" Plan uniquement: aucun geste ni dépense exécuté.")
        }

        return Plan(command, multiplier, affordable, suggested, reserve, explanation)
    }

    private fun blocked(reason: String) = Plan(
        SpinCommand.BLOCKED, null, emptyList(), null, null,
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
