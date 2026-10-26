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
        entry("purrdoku", listOf("cats"), MechanicFamily.DEDUCTION, LibraryStage.PILOT, solved + ActivityCapability.HINTS),
        entry("untangle", family = MechanicFamily.SPATIAL, stage = LibraryStage.PILOT, capabilities = generated + ActivityCapability.SOLUTION_WITNESS),
        entry("route", listOf("waypoints"), MechanicFamily.ROUTE, LibraryStage.PILOT, solved + ActivityCapability.HINTS),
        entry("shikaku", family = MechanicFamily.PACKING, stage = LibraryStage.PILOT, capabilities = solved + ActivityCapability.HINTS),
        entry("habitat-search", listOf("wildlife-survey"), MechanicFamily.OBSERVATION, LibraryStage.PILOT, solved + ActivityCapability.HINTS),
        entry("sokoban", listOf("cargo-push"), MechanicFamily.MOVEMENT, LibraryStage.PILOT, solved + ActivityCapability.HINTS),
        entry("packing", listOf("cargo-packing"), MechanicFamily.PACKING, LibraryStage.PILOT, solved + ActivityCapability.HINTS),
        research("color-neighbors", family = MechanicFamily.DEDUCTION),
        research("spatial-deduction", listOf("murr-doku"), MechanicFamily.DEDUCTION),
        research("grid-move", family = MechanicFamily.MOVEMENT),
        research("one-stroke", family = MechanicFamily.ROUTE),
        research("nonogram", family = MechanicFamily.DEDUCTION),
        research("codebreaker", family = MechanicFamily.DEDUCTION),
        research("connect-pairs", family = MechanicFamily.ROUTE),
        research("maze", family = MechanicFamily.ROUTE),
        research("hashi", family = MechanicFamily.NETWORK),
        research("slitherlink", family = MechanicFamily.ROUTE),
        research("masyu", family = MechanicFamily.ROUTE),
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

private fun research(
    mechanicId: String,
    aliases: List<String> = emptyList(),
    family: MechanicFamily,
) = entry(mechanicId, aliases, family, LibraryStage.RESEARCH, emptyList())
