package dev.smallwins.puzzle.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LibraryStage {
    @SerialName("pilot")
    PILOT,

    @SerialName("candidate")
    CANDIDATE,

    @SerialName("research")
    RESEARCH,
}

@Serializable
enum class MechanicFamily {
    @SerialName("deduction")
    DEDUCTION,

    @SerialName("network")
    NETWORK,

    @SerialName("spatial")
    SPATIAL,

    @SerialName("route")
    ROUTE,

    @SerialName("packing")
    PACKING,

    @SerialName("observation")
    OBSERVATION,

    @SerialName("movement")
    MOVEMENT,
}

@Serializable
data class MechanicCatalogEntry(
    val mechanicId: String,
    val aliases: List<String>,
    val family: MechanicFamily,
    val stage: LibraryStage,
    val capabilities: List<ActivityCapability>,
)

private val generated =
    listOf(
        ActivityCapability.DETERMINISTIC_GENERATION,
        ActivityCapability.DIFFICULTY,
        ActivityCapability.SERIALIZABLE_STATE,
    )
private val solved = generated + listOf(ActivityCapability.SOLVER, ActivityCapability.SOLUTION_WITNESS)

val mechanicCatalog: List<MechanicCatalogEntry> =
    listOf(
        entry("network", listOf("pipes"), MechanicFamily.NETWORK, LibraryStage.PILOT, solved + ActivityCapability.HINTS),
        entry("purrdoku", listOf("cats"), MechanicFamily.DEDUCTION, capabilities = solved + ActivityCapability.HINTS),
        entry("untangle", family = MechanicFamily.SPATIAL, capabilities = generated),
        entry("route", listOf("waypoints"), MechanicFamily.ROUTE, capabilities = solved + ActivityCapability.HINTS),
        entry("shikaku", family = MechanicFamily.PACKING, capabilities = solved + ActivityCapability.HINTS),
        entry("habitat-search", listOf("wildlife-survey"), MechanicFamily.OBSERVATION, capabilities = generated),
        entry("sokoban", family = MechanicFamily.MOVEMENT, capabilities = solved),
        entry("packing", family = MechanicFamily.PACKING, capabilities = generated),
        entry("color-neighbors", family = MechanicFamily.DEDUCTION, capabilities = generated),
        entry("spatial-deduction", listOf("murr-doku"), MechanicFamily.DEDUCTION, capabilities = generated),
        entry("grid-move", family = MechanicFamily.MOVEMENT, capabilities = listOf(ActivityCapability.DIFFICULTY, ActivityCapability.SERIALIZABLE_STATE)),
        entry("one-stroke", family = MechanicFamily.ROUTE, capabilities = generated),
        entry("nonogram", family = MechanicFamily.DEDUCTION, capabilities = generated),
        entry("codebreaker", family = MechanicFamily.DEDUCTION, capabilities = generated),
        entry("connect-pairs", family = MechanicFamily.ROUTE, capabilities = listOf(ActivityCapability.DIFFICULTY, ActivityCapability.SERIALIZABLE_STATE)),
        entry("maze", family = MechanicFamily.ROUTE, capabilities = generated),
        entry("hashi", family = MechanicFamily.NETWORK, stage = LibraryStage.RESEARCH, capabilities = generated),
        entry("slitherlink", family = MechanicFamily.ROUTE, stage = LibraryStage.RESEARCH, capabilities = generated),
        entry("masyu", family = MechanicFamily.ROUTE, stage = LibraryStage.RESEARCH, capabilities = generated),
    )

fun canonicalMechanicId(id: String): String? =
    mechanicCatalog.firstOrNull { it.mechanicId == id || id in it.aliases }?.mechanicId

private fun entry(
    mechanicId: String,
    aliases: List<String> = emptyList(),
    family: MechanicFamily,
    stage: LibraryStage = LibraryStage.CANDIDATE,
    capabilities: List<ActivityCapability>,
) = MechanicCatalogEntry(mechanicId, aliases, family, stage, capabilities)
