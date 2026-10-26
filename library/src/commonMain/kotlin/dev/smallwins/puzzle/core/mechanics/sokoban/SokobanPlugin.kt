package dev.smallwins.puzzle.core.mechanics.sokoban

import dev.smallwins.puzzle.core.ActivityCapability
import dev.smallwins.puzzle.core.ActivityManifest
import dev.smallwins.puzzle.core.ActivityPlugin
import dev.smallwins.puzzle.core.DifficultyBand
import dev.smallwins.puzzle.core.EngineEvent
import dev.smallwins.puzzle.core.Evaluation
import dev.smallwins.puzzle.core.GenerationRequest
import dev.smallwins.puzzle.core.GenerationResult
import dev.smallwins.puzzle.core.PuzzleHint
import dev.smallwins.puzzle.core.PuzzleIdentity
import dev.smallwins.puzzle.core.PuzzleLevel
import dev.smallwins.puzzle.core.StableRandom
import dev.smallwins.puzzle.core.Transition
import dev.smallwins.puzzle.core.ValidationIssue
import dev.smallwins.puzzle.core.ValidationResult
import dev.smallwins.puzzle.core.stableHash
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

const val SOKOBAN_RULES_VERSION = 1
const val SOKOBAN_GENERATOR_VERSION = 3

object SokobanPlugin : ActivityPlugin<SokobanLevel, SokobanState, SokobanAction, SokobanWitness, SokobanEvaluationMetrics, SokobanGeneratorMetrics> {
    private val directionNames = listOf("up", "right", "down", "left")

    override val manifest = ActivityManifest(
        mechanicId = "sokoban",
        rulesVersion = SOKOBAN_RULES_VERSION,
        aliases = listOf("cargo-push"),
        capabilities = listOf(
            ActivityCapability.DETERMINISTIC_GENERATION,
            ActivityCapability.DIFFICULTY,
            ActivityCapability.SOLVER,
            ActivityCapability.HINTS,
            ActivityCapability.SERIALIZABLE_STATE,
            ActivityCapability.SOLUTION_WITNESS,
        ),
    )

    override fun validateLevel(level: SokobanLevel): ValidationResult {
        val p = level.payload; val issues = mutableListOf<ValidationIssue>(); val cells = p.size * p.size
        if (level.identity.mechanicId != manifest.mechanicId) issues += issue("identity.mechanic", "$.identity.mechanicId")
        if (level.identity.rulesVersion != SOKOBAN_RULES_VERSION) issues += issue("identity.rules-version", "$.identity.rulesVersion")
        if (p.size !in 5..12) issues += issue("grid.size", "$.payload.size")
        if (p.player !in 0 until cells) issues += issue("player.invalid", "$.payload.player")
        if (p.boxes.isEmpty() || p.boxes.size != p.goals.size || p.boxes.distinct().size != p.boxes.size || p.goals.distinct().size != p.goals.size) issues += issue("targets.invalid", "$.payload")
        if ((p.boxes + p.goals + p.innerWalls).any { it !in 0 until cells }) issues += issue("cell.invalid", "$.payload")
        if (p.player in p.boxes || p.player in p.innerWalls || p.boxes.any { it in p.innerWalls } || p.goals.any { it in p.innerWalls }) issues += issue("overlap.invalid", "$.payload")
        return ValidationResult(issues.isEmpty(), issues.distinct())
    }

    override fun initialState(level: SokobanLevel) = SokobanState(level.payload.player, level.payload.boxes.sorted())

    override fun validateState(level: SokobanLevel, state: SokobanState): ValidationResult {
        val walls = walls(level.payload); val cells = level.payload.size * level.payload.size
        val valid = state.player in 0 until cells && state.player !in walls && state.player !in state.boxes &&
            state.boxes.size == level.payload.boxes.size && state.boxes.distinct().size == state.boxes.size && state.boxes.all { it in 0 until cells && it !in walls }
        return if (valid) ValidationResult(true) else ValidationResult(false, listOf(issue("state.invalid", "$.state")))
    }

    override fun transition(level: SokobanLevel, state: SokobanState, action: SokobanAction): Transition<SokobanState> {
        if (action.type != "move" || action.direction !in directionNames) return rejected(state, "invalid-action")
        val delta = delta(level.payload.size, action.direction); val next = state.player + delta; val walls = walls(level.payload)
        if (horizontalWrap(level.payload.size, state.player, next, action.direction) || next in walls) return rejected(state, "blocked")
        val boxes = state.boxes.toMutableSet(); var pushed = false
        if (next in boxes) {
            val beyond = next + delta
            if (horizontalWrap(level.payload.size, next, beyond, action.direction) || beyond in walls || beyond in boxes) return rejected(state, "box-blocked")
            boxes.remove(next); boxes.add(beyond); pushed = true
        }
        val nextState = SokobanState(next, boxes.sorted())
        return Transition(true, true, nextState, events = listOf(EngineEvent(if (pushed) "box-pushed" else "player-moved", JsonObject(mapOf("direction" to JsonPrimitive(action.direction))))))
    }

    override fun evaluate(level: SokobanLevel, state: SokobanState): Evaluation<SokobanEvaluationMetrics> {
        val onGoals = level.payload.goals.count { it in state.boxes }
        return Evaluation(onGoals == level.payload.goals.size, onGoals.toDouble() / level.payload.goals.size, SokobanEvaluationMetrics(onGoals, level.payload.goals.size))
    }

    override fun generate(request: GenerationRequest): GenerationResult<SokobanLevel, SokobanWitness, SokobanGeneratorMetrics> {
        require(request.generatorVersion == SOKOBAN_GENERATOR_VERSION)
        if (request.difficulty.band == DifficultyBand.TUTORIAL) {
            val payload = SokobanPayload(6, 13, listOf(14), listOf(16), emptyList())
            val level = level(request, payload, 0)
            return GenerationResult(level, SokobanWitness(listOf("right", "right"), 2), SokobanGeneratorMetrics(6, 1, 2, 2, 0))
        }
        val t = request.difficulty.normalized.coerceIn(0.0, 1.0); val size = scale(t, 7, 8); val boxCount = 2
        val random = StableRandom("sokoban:v3:regular:$t:${request.seed}:${request.variant ?: "1"}")
        val directions = directions(size); val opposite = mapOf("up" to "down", "down" to "up", "left" to "right", "right" to "left")
        val interior = (1 until size - 1).flatMap { y -> (1 until size - 1).map { x -> y * size + x } }
        repeat(800) { attempt ->
            val goals = random.shuffled(interior.filter { val y = it / size; val x = it % size; x > 1 && x < size - 2 && y > 1 && y < size - 2 }).take(boxCount)
            val innerWalls = mutableListOf<Int>()
            val anchors = random.shuffled(interior.filter { it % size == 1 || it % size == size - 2 || it / size == 1 || it / size == size - 2 })
            anchors.take(2).forEach { anchor ->
                val d = when { anchor % size == 1 -> 1; anchor % size == size - 2 -> -1; anchor / size == 1 -> size; else -> -size }
                repeat(scale(t, 2, 3)) { k -> (anchor + k * d).let { if (it !in goals && it !in innerWalls) innerWalls += it } }
            }
            val payloadWalls = SokobanPayload(size, 0, emptyList(), goals, innerWalls); val wallSet = walls(payloadWalls)
            var boxes = goals.toMutableSet(); var player = random.shuffled(interior.filter { it !in wallSet && it !in boxes }).first()
            val reverseMoves = mutableListOf<String>(); val seen = mutableSetOf(goals.sorted().joinToString(",")); var pulls = 0
            repeat(scale(t, 18, 38)) {
                val reachable = reachablePaths(size, player, boxes, wallSet)
                data class Pull(val stand: Int, val next: Int, val name: String, val boxes: MutableSet<Int>, val key: String)
                val options = mutableListOf<Pull>()
                boxes.forEach { box -> directions.forEach { (name, d) ->
                    val stand = box + d; val next = box + 2 * d
                    if (stand !in reachable || next in wallSet || next in boxes) return@forEach
                    val nextBoxes = boxes.toMutableSet().also { it.remove(box); it.add(stand) }; val key = nextBoxes.sorted().joinToString(",")
                    if (key !in seen) options += Pull(stand, next, name, nextBoxes, key)
                } }
                if (options.isEmpty()) return@repeat
                val choice = options[random.nextInt(options.size)]
                reverseMoves += reachable.getValue(choice.stand); reverseMoves += choice.name
                player = choice.next; boxes = choice.boxes; seen += choice.key; pulls += 1
            }
            if (pulls < scale(t, 8, 14) || boxes.any { it in goals }) return@repeat
            val payload = SokobanPayload(size, player, boxes.sorted(), goals, innerWalls)
            val analysis = analyze(payload, 12_000)
            if (analysis.status != "solved" || (analysis.minimumPushes ?: 0) < scale(t, 6, 10)) return@repeat
            val open = analyze(payload.copy(innerWalls = emptyList()), 12_000)
            if (open.status != "solved" || (open.minimumPushes ?: Int.MAX_VALUE) >= analysis.minimumPushes!!) return@repeat
            val solution = reverseMoves.asReversed().map { opposite.getValue(it) }
            val level = level(request, payload, attempt)
            return GenerationResult(level, SokobanWitness(solution, analysis.minimumPushes), SokobanGeneratorMetrics(size, boxCount, pulls, analysis.minimumPushes, analysis.minimumPushes - open.minimumPushes!!))
        }
        error("Could not construct a nontrivial Sokoban level for this seed")
    }

    override fun solve(level: SokobanLevel): SokobanWitness? {
        val analysis = analyze(level.payload, 50_000)
        return if (analysis.status == "solved") SokobanWitness(analysis.moves, analysis.minimumPushes!!) else null
    }

    override fun hint(level: SokobanLevel, state: SokobanState, used: Int): PuzzleHint? {
        val analysis = analyze(level.payload.copy(player = state.player, boxes = state.boxes), 50_000)
        val move = analysis.moves.firstOrNull() ?: return null
        return PuzzleHint(if (used < 2) "direction" else "exact-move", JsonObject(mapOf("direction" to JsonPrimitive(move))))
    }

    fun analyze(payload: SokobanPayload, budget: Int = 12_000): SokobanAnalysis {
        val wallSet = walls(payload); val goals = payload.goals.toSet(); val dirs = directions(payload.size)
        data class Node(val boxes: Set<Int>, val reachable: Set<Int>, val pushes: Int, val moves: List<String>)
        val queue = mutableListOf<Node>(); val seen = mutableSetOf<String>()
        fun add(player: Int, boxes: Set<Int>, pushes: Int, moves: List<String>) {
            val paths = reachablePaths(payload.size, player, boxes, wallSet); val cells = paths.keys
            val key = "${cells.minOrNull()}|${boxes.sorted().joinToString(",")}"
            if (seen.add(key)) queue += Node(boxes, cells, pushes, moves)
        }
        add(payload.player, payload.boxes.toSet(), 0, emptyList()); var expanded = 0; var choices = 0; var cursor = 0
        while (cursor < queue.size) {
            if (expanded >= budget) return SokobanAnalysis("budget", expanded = expanded, choices = choices)
            val node = queue[cursor++]; expanded += 1
            if (goals.all { it in node.boxes }) return SokobanAnalysis("solved", node.pushes, expanded, choices, node.moves)
            var options = 0
            node.boxes.forEach { box -> dirs.forEach { (name, d) ->
                val next = box + d
                if (box - d !in node.reachable || next in wallSet || next in node.boxes) return@forEach
                if (next !in goals && (next - 1 in wallSet || next + 1 in wallSet) && (next - payload.size in wallSet || next + payload.size in wallSet)) return@forEach
                val moved = node.boxes.toMutableSet().also { it.remove(box); it.add(next) }
                add(box, moved, node.pushes + 1, node.moves + name); options += 1
            } }
            if (options > 1) choices += 1
        }
        return SokobanAnalysis("unsolvable", expanded = expanded, choices = choices)
    }

    private fun level(request: GenerationRequest, payload: SokobanPayload, attempt: Int) = PuzzleLevel(
        PuzzleIdentity("sokoban", "sokoban", "generated", "generated-${stableHash("v3/${request.seed}/$attempt").toString(16)}", 1, SOKOBAN_RULES_VERSION, 1, SOKOBAN_GENERATOR_VERSION, request.seed),
        request.difficulty,
        payload,
    )
}

private fun walls(payload: SokobanPayload): Set<Int> = buildSet {
    addAll(payload.innerWalls)
    repeat(payload.size * payload.size) { i -> if (i % payload.size == 0 || i % payload.size == payload.size - 1 || i < payload.size || i >= payload.size * (payload.size - 1)) add(i) }
}

private fun reachablePaths(size: Int, player: Int, boxes: Set<Int>, walls: Set<Int>): Map<Int, List<String>> {
    val paths = mutableMapOf(player to emptyList<String>()); val queue = mutableListOf(player); var cursor = 0
    while (cursor < queue.size) {
        val cell = queue[cursor++]
        directions(size).forEach { (name, d) ->
            val next = cell + d
            if (horizontalWrap(size, cell, next, name) || next in walls || next in boxes || next in paths) return@forEach
            paths[next] = paths.getValue(cell) + name; queue += next
        }
    }
    return paths
}

private fun directions(size: Int) = listOf("up" to -size, "right" to 1, "down" to size, "left" to -1)
private fun delta(size: Int, direction: String) = directions(size).first { it.first == direction }.second
private fun horizontalWrap(size: Int, from: Int, to: Int, direction: String) = direction in setOf("left", "right") && from / size != to / size
private fun scale(t: Double, a: Int, b: Int) = kotlin.math.round(a + (b - a) * t).toInt()
private fun issue(code: String, path: String) = ValidationIssue(code, path)
private fun rejected(state: SokobanState, reason: String) = Transition(false, false, state, reason)
