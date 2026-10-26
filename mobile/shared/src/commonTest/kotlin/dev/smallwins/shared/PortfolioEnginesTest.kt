package dev.smallwins.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

class PortfolioEnginesTest {
    @Test
    fun purrdoku_tutorial_accepts_its_unique_arrangement() {
        var state = PurrdokuEngine.initial(purrdokuTutorial)
        state = PurrdokuEngine.place(purrdokuTutorial, state, CatId.GINGER, 0)
        state = PurrdokuEngine.place(purrdokuTutorial, state, CatId.BLACK, 4)
        state = PurrdokuEngine.place(purrdokuTutorial, state, CatId.WHITE, 8)

        assertTrue(PurrdokuEngine.solved(purrdokuTutorial, state))
    }

    @Test
    fun untangle_detects_a_crossing_and_accepts_a_square() {
        val initial = UntangleEngine.initial(untangleTutorial)
        val square = UntangleState(
            mapOf(
                "A" to PuzzlePoint(20f, 20f),
                "B" to PuzzlePoint(80f, 20f),
                "C" to PuzzlePoint(80f, 80f),
                "D" to PuzzlePoint(20f, 80f),
            ),
        )

        assertFalse(UntangleEngine.solved(untangleTutorial, initial))
        assertTrue(UntangleEngine.solved(untangleTutorial, square))
    }

    @Test
    fun waypoints_tutorial_requires_every_carrot_before_the_finish() {
        var state = WaypointsEngine.initial(waypointsTutorial)
        listOf(3, 6, 7, 8, 5).forEach { state = WaypointsEngine.add(waypointsTutorial, state, it) }

        assertTrue(WaypointsEngine.solved(waypointsTutorial, state))
    }

    @Test
    fun shikaku_tutorial_can_be_partitioned_into_valid_rectangles() {
        var state = ShikakuEngine.initial(shikakuTutorial)
        listOf(0 to 3, 1 to 5, 6 to 7, 8 to 8).forEach { (first, second) ->
            state = ShikakuEngine.place(shikakuTutorial, state, first, second)
        }

        assertTrue(ShikakuEngine.solved(shikakuTutorial, state))
    }

    @Test
    fun every_first_level_has_an_explicit_solution_witness() {
        var cats = PurrdokuEngine.initial(purrdokuLevelOne)
        cats = PurrdokuEngine.place(purrdokuLevelOne, cats, CatId.WHITE, 0)
        cats = PurrdokuEngine.place(purrdokuLevelOne, cats, CatId.GINGER, 5)
        cats = PurrdokuEngine.place(purrdokuLevelOne, cats, CatId.BLACK, 7)
        assertTrue(PurrdokuEngine.solved(purrdokuLevelOne, cats))

        val untangled = UntangleState(
            mapOf(
                "A" to PuzzlePoint(50f, 10f),
                "B" to PuzzlePoint(90f, 40f),
                "C" to PuzzlePoint(75f, 85f),
                "D" to PuzzlePoint(25f, 85f),
                "E" to PuzzlePoint(10f, 40f),
            ),
        )
        assertTrue(UntangleEngine.solved(untangleLevelOne, untangled))

        var route = WaypointsEngine.initial(waypointsLevelOne)
        listOf(4, 5, 6, 7, 3, 2, 1).forEach { route = WaypointsEngine.add(waypointsLevelOne, route, it) }
        assertTrue(WaypointsEngine.solved(waypointsLevelOne, route))

        var rectangles = ShikakuEngine.initial(shikakuLevelOne)
        listOf(0 to 0, 1 to 2, 3 to 5, 6 to 8).forEach { (first, second) ->
            rectangles = ShikakuEngine.place(shikakuLevelOne, rectangles, first, second)
        }
        assertTrue(ShikakuEngine.solved(shikakuLevelOne, rectangles))

    }

    @Test
    fun native_level_packs_match_the_seven_level_web_catalog() {
        val ids = listOf("tutorial") + (1..6).map { "level-$it" }
        listOf(
            purrdokuLevels.map { it.id }, pipesLevels.map { it.id }, untangleLevels.map { it.id },
            waypointsLevels.map { it.id }, shikakuLevels.map { it.id },
        ).forEach { assertEquals(ids, it) }

        purrdokuLevels.forEach { assertEquals(1, PurrdokuEngine.solutions(it).size) }
        pipesLevels.forEach { level -> assertTrue(PipesEngine.solved(level, PipesState(List(level.ports.size) { 0 }))) }
        untangleLevels.forEach { level -> assertTrue(UntangleEngine.solved(level, solvedUntangleState(level))) }
        waypointsLevels.forEach { level -> assertTrue(WaypointsEngine.completion(level, WaypointsEngine.initial(level)) != null) }
        shikakuLevels.forEach { assertEquals(1, ShikakuEngine.solutions(it).size) }
    }

    @Test
    fun generators_are_deterministic_and_produce_solvable_levels() {
        val seed = "portfolio-test"
        assertEquals(LevelGenerator.purrdoku(seed), LevelGenerator.purrdoku(seed))
        assertEquals(LevelGenerator.pipes(seed), LevelGenerator.pipes(seed))
        assertEquals(LevelGenerator.untangle(seed), LevelGenerator.untangle(seed))
        assertEquals(LevelGenerator.waypoints(seed), LevelGenerator.waypoints(seed))
        assertEquals(LevelGenerator.shikaku(seed), LevelGenerator.shikaku(seed))

        assertEquals(1, PurrdokuEngine.solutions(LevelGenerator.purrdoku(seed)).size)
        LevelGenerator.pipes(seed).let { level ->
            assertTrue(PipesEngine.solved(level, PipesState(List(level.ports.size) { 0 })))
        }
        assertTrue(UntangleEngine.conflictEdges(LevelGenerator.untangle(seed), UntangleEngine.initial(LevelGenerator.untangle(seed))).isNotEmpty())
        LevelGenerator.untangle(seed).let { level -> assertTrue(UntangleEngine.solved(level, solvedUntangleState(level))) }
        LevelGenerator.waypoints(seed).let { level ->
            assertTrue(WaypointsEngine.completion(level, WaypointsEngine.initial(level)) != null)
        }
        assertEquals(1, ShikakuEngine.solutions(LevelGenerator.shikaku(seed)).size)
    }

    @Test
    fun progressive_hints_match_the_four_hint_enabled_web_mechanics() {
        val cats = PurrdokuEngine.initial(purrdokuTutorial)
        assertTrue(PurrdokuEngine.hint(purrdokuTutorial, cats, 0)?.startsWith("Extra clue 1") == true)
        assertTrue(PurrdokuEngine.hint(purrdokuTutorial, cats, 1)?.startsWith("Extra clue 2") == true)
        assertTrue(PurrdokuEngine.hint(purrdokuTutorial, cats, 4)?.startsWith("Exact spot") == true)

        val pipes = PipesEngine.initial(pipesLevelOne)
        assertTrue(PipesEngine.hint(pipesLevelOne, pipes, 0)?.contains("Look in row") == true)
        assertTrue(PipesEngine.hint(pipesLevelOne, pipes, 1)?.contains("Focus on the pipe") == true)
        assertTrue(PipesEngine.hint(pipesLevelOne, pipes, 2)?.contains("One working arrangement") == true)

        val route = WaypointsEngine.initial(waypointsLevelOne)
        assertTrue(WaypointsEngine.hint(waypointsLevelOne, route, 0)?.contains("Look toward") == true)
        assertTrue(WaypointsEngine.hint(waypointsLevelOne, route, 1)?.contains("Aim for") == true)
        assertTrue(WaypointsEngine.hint(waypointsLevelOne, route, 2)?.contains("Next step") == true)

        val rectangles = ShikakuEngine.initial(shikakuLevelOne)
        assertTrue(ShikakuEngine.hint(shikakuLevelOne, rectangles, 0)?.contains("Start with clue") == true)
        assertTrue(ShikakuEngine.hint(shikakuLevelOne, rectangles, 1)?.contains("Try the rectangle") == true)
    }

    private fun solvedUntangleState(level: UntangleLevel): UntangleState {
        val count = level.nodes.size
        return UntangleState(
            level.nodes.mapIndexed { index, node ->
                val angle = -PI / 2 + index * 2 * PI / count
                node.id to PuzzlePoint(
                    (round((50 + 37 * cos(angle)) * 10) / 10).toFloat(),
                    (round((50 + 37 * sin(angle)) * 10) / 10).toFloat(),
                )
            }.toMap(),
        )
    }
}
