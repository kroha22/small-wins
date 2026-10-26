package dev.smallwins.puzzle.core

import dev.smallwins.puzzle.core.mechanics.colorneighbors.ColorNeighborsPlugin
import dev.smallwins.puzzle.core.mechanics.network.NetworkPlugin
import dev.smallwins.puzzle.core.mechanics.packing.PackingPlugin
import dev.smallwins.puzzle.core.mechanics.placement.PlacementPlugin
import dev.smallwins.puzzle.core.mechanics.route.RoutePlugin
import dev.smallwins.puzzle.core.mechanics.sokoban.SokobanPlugin
import dev.smallwins.puzzle.core.mechanics.untangle.UntanglePlugin
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LudeniCompatibilityProfilesTest {
    private fun request(
        seed: String = "first",
        version: Int = 1,
        parameters: JsonObject = JsonObject(mapOf("profile" to JsonPrimitive("ludeni-v3"))),
    ) = GenerationRequest(seed, version, Difficulty(DifficultyBand.EASY, 0.0), variant = "1", parameters = parameters)

    @Test
    fun legacyFiveGameProfilesKeepFrozenV3Fingerprints() {
        val network = NetworkPlugin.generate(request()).level.payload
        assertEquals(4, network.rows)
        assertEquals(3, network.sourceCell)
        assertEquals(10, network.targetCell)
        assertEquals(listOf(3), network.ports[3])

        val route = RoutePlugin.generate(request())
        assertEquals(listOf("cell-3", "cell-11", "cell-7"), route.level.payload.forbiddenNodeIds)
        assertEquals(listOf("cell-6", "cell-13"), route.level.payload.waypoints.map { it.nodeId })
        assertEquals("cell-15", route.solutionWitness.paths.single().nodeIds.last())

        val untangle = UntanglePlugin.generate(request())
        assertEquals(6, untangle.level.payload.nodes.size)
        assertEquals(7, untangle.level.payload.edges.size)
        assertTrue(UntanglePlugin.evaluate(untangle.level, untangle.solutionWitness).solved)

        val packing = PackingPlugin.generate(request(version = 3, parameters = JsonObject(emptyMap())))
        assertEquals(4, packing.level.payload.size)
        assertEquals(listOf(4, 10, 8, 0), packing.solutionWitness.origins)

        val sokoban = SokobanPlugin.generate(request(version = 3, parameters = JsonObject(emptyMap())))
        assertEquals(listOf(18, 32), sokoban.level.payload.boxes)
        assertEquals(6, sokoban.metrics.minimumPushes)
    }

    @Test
    fun continuationProfilesAreDeterministicUniqueAndSolvedByWitnesses() {
        val parameters = JsonObject(mapOf("profile" to JsonPrimitive("ludeni-continuation-v1"), "size" to JsonPrimitive(5), "stage" to JsonPrimitive(0)))
        val catsRequest = GenerationRequest("cats:continuation:1:32", 1, Difficulty(DifficultyBand.HARD, 1.0), variant = "1", parameters = parameters)
        val cats = PlacementPlugin.generate(catsRequest)
        assertEquals(cats, PlacementPlugin.generate(catsRequest))
        assertTrue(PlacementPlugin.evaluate(cats.level, cats.solutionWitness).solved)
        assertEquals(1, PlacementPlugin.solveDetails(cats.level, limit = 2).solutions.size)

        val neighborsRequest = catsRequest.copy(seed = "color-neighbors:continuation:1:32")
        val neighbors = ColorNeighborsPlugin.generate(neighborsRequest)
        assertEquals(neighbors, ColorNeighborsPlugin.generate(neighborsRequest))
        assertTrue(ColorNeighborsPlugin.evaluate(neighbors.level, neighbors.solutionWitness).solved)
        assertEquals(1, ColorNeighborsPlugin.solveDetails(neighbors.level, limit = 2).solutions.size)
    }
}
