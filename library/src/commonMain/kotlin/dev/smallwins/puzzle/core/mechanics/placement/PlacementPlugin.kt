package dev.smallwins.puzzle.core.mechanics.placement

import dev.smallwins.puzzle.core.ActivityCapability
import dev.smallwins.puzzle.core.ActivityManifest
import dev.smallwins.puzzle.core.ActivityPlugin
import dev.smallwins.puzzle.core.ContinuationRandom
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

const val PLACEMENT_RULES_VERSION = 1
const val PLACEMENT_GENERATOR_VERSION = 1

object PlacementPlugin :
    ActivityPlugin<
        PlacementLevel,
        PlacementState,
        PlacementAction,
        PlacementWitness,
        PlacementEvaluationMetrics,
        PlacementGeneratorMetrics,
    > {
    override val manifest =
        ActivityManifest(
            mechanicId = "purrdoku",
            rulesVersion = PLACEMENT_RULES_VERSION,
            aliases = listOf("cats"),
            capabilities =
                listOf(
                    ActivityCapability.DETERMINISTIC_GENERATION,
                    ActivityCapability.DIFFICULTY,
                    ActivityCapability.SOLVER,
                    ActivityCapability.HINTS,
                    ActivityCapability.SERIALIZABLE_STATE,
                    ActivityCapability.SOLUTION_WITNESS,
                ),
        )

    override fun validateLevel(level: PlacementLevel): ValidationResult {
        val p = level.payload
        val issues = mutableListOf<ValidationIssue>()
        if (level.identity.mechanicId != manifest.mechanicId) issues += issue("identity.mechanic", "$.identity.mechanicId")
        if (level.identity.rulesVersion != PLACEMENT_RULES_VERSION) issues += issue("identity.rules-version", "$.identity.rulesVersion")
        if (p.size !in 2..8) issues += issue("grid.size", "$.payload.size")
        val cells = p.size * p.size
        val ids = p.entities.map { it.id }
        if (ids.size != p.size || ids.any { it.isBlank() } || ids.distinct().size != ids.size) {
            issues += issue("entities.invalid", "$.payload.entities")
        }
        val zoneCells = p.zones.flatMap { it.cells }
        if (
            p.zones.size != p.size ||
            p.zones.map { it.id }.distinct().size != p.zones.size ||
            p.zones.any { it.id.isBlank() || it.cells.isEmpty() } ||
            zoneCells.size != cells || zoneCells.toSet() != (0 until cells).toSet()
        ) issues += issue("zones.invalid", "$.payload.zones")
        if (
            p.features.size != p.zones.size ||
            p.features.map { it.id }.distinct().size != p.features.size ||
            p.features.map { it.cell }.distinct().size != p.features.size ||
            p.features.any { feature ->
                feature.id.isBlank() || feature.cell !in 0 until cells ||
                    p.zones.none { feature.cell in it.cells }
            } ||
            p.features.map { feature -> p.zones.indexOfFirst { feature.cell in it.cells } }.distinct().size != p.features.size
        ) issues += issue("features.invalid", "$.payload.features")
        if (p.clues.isEmpty()) issues += issue("clues.empty", "$.payload.clues")
        p.clues.forEachIndexed { index, clue ->
            if (!validClue(p, clue)) issues += issue("clue.invalid", "$.payload.clues[$index]")
        }
        return ValidationResult(issues.isEmpty(), issues.distinct())
    }

    override fun initialState(level: PlacementLevel) =
        PlacementState(level.payload.entities.associate { it.id to null })

    override fun validateState(
        level: PlacementLevel,
        state: PlacementState,
    ): ValidationResult {
        val p = level.payload
        val cells = state.placements.values.filterNotNull()
        val valid =
            state.placements.keys == p.entities.map { it.id }.toSet() &&
                cells.all { it in 0 until p.size * p.size && featureAt(p, it) == null } &&
                cells.distinct().size == cells.size
        return if (valid) ValidationResult(true) else ValidationResult(false, listOf(issue("placements.invalid", "$.placements")))
    }

    override fun transition(
        level: PlacementLevel,
        state: PlacementState,
        action: PlacementAction,
    ): Transition<PlacementState> {
        val p = level.payload
        if (action.entity !in state.placements) return rejected(state, "unknown-entity")
        if (action.type == "remove") {
            if (state.placements[action.entity] == null) return Transition(true, false, state)
            return accepted(state.copy(placements = state.placements + (action.entity to null)), "entity-removed", action.entity)
        }
        val cell = action.cell
        if (action.type != "place" || cell == null || cell !in 0 until p.size * p.size) {
            return rejected(state, "invalid-cell")
        }
        if (featureAt(p, cell) != null) return rejected(state, "feature-occupied")
        if (state.placements[action.entity] == cell) return Transition(true, false, state)
        val occupant = state.placements.entries.firstOrNull { it.key != action.entity && it.value == cell }?.key
        val previous = state.placements[action.entity]
        val next = state.placements.toMutableMap()
        next[action.entity] = cell
        if (occupant != null) next[occupant] = previous
        return accepted(PlacementState(next), if (occupant == null) "entity-placed" else "entities-swapped", action.entity)
    }

    override fun evaluate(
        level: PlacementLevel,
        state: PlacementState,
    ): Evaluation<PlacementEvaluationMetrics> {
        val p = level.payload
        val inspection = inspect(level, state)
        val placed = state.placements.values.count { it != null }
        val satisfied = inspection.clueStatuses.count { it == "satisfied" }
        val solved =
            placed == p.entities.size && inspection.conflicts.isEmpty() &&
                inspection.clueStatuses.all { it == "satisfied" }
        return Evaluation(
            solved = solved,
            progress = if (solved) 1.0 else (placed + satisfied).toDouble() / (p.entities.size + p.clues.size),
            metrics = PlacementEvaluationMetrics(placed, p.entities.size, inspection.conflicts, satisfied),
        )
    }

    override fun generate(request: GenerationRequest): GenerationResult<PlacementLevel, PlacementWitness, PlacementGeneratorMetrics> {
        require(request.generatorVersion == PLACEMENT_GENERATOR_VERSION)
        if ((request.parameters["profile"] as? JsonPrimitive)?.contentOrNull == "ludeni-continuation-v1") return generateLudeniContinuation(request)
        val defaultSize = if (request.difficulty.band in setOf(DifficultyBand.HARD, DifficultyBand.EXPERT)) 4 else 3
        val size = request.parameters["size"].let { (it as? JsonPrimitive)?.intOrNull ?: defaultSize }.coerceIn(2, 8)
        val random = StableRandom("purrdoku/v$PLACEMENT_GENERATOR_VERSION/${request.seed}/$size")
        val entities = List(size) { PlacementEntity("entity-${it + 1}") }
        val columns = random.shuffled((0 until size).toList())
        val solution = PlacementState(entities.mapIndexed { row, entity -> entity.id to (row * size + columns[row]) }.toMap())
        val zones = List(size) { row -> PlacementZone("zone-${row + 1}", List(size) { column -> row * size + column }) }
        val features = zones.mapIndexed { index, zone ->
            val available = zone.cells.filter { it != solution.placements[entities[index].id] }
            PlacementFeature("feature-${index + 1}", available[random.nextInt(available.size)])
        }
        val candidates = mutableListOf<PlacementClue>()
        entities.forEach { entity ->
            val cell = solution.placements.getValue(entity.id)!!
            candidates += PlacementClue("inRow", entity.id, value = cell / size)
            candidates += PlacementClue("inColumn", entity.id, value = cell % size)
            candidates += PlacementClue("inZone", entity.id, zone = zoneAt(PlacementPayload(size, entities, zones, features, emptyList()), cell))
        }
        entities.forEach { first ->
            entities.filter { it != first }.forEach { second ->
                val a = solution.placements.getValue(first.id)!!
                val b = solution.placements.getValue(second.id)!!
                candidates += PlacementClue(if (a % size < b % size) "leftOf" else "rightOf", first.id, other = second.id)
            }
        }
        var payload = PlacementPayload(size, entities, zones, features, emptyList())
        var nodes = 0
        random.shuffled(candidates).forEach { clue ->
            if (solveDetails(levelForGeneration(request, payload), limit = 2, budget = 100_000).solutions.size == 1) return@forEach
            payload = payload.copy(clues = payload.clues + clue)
            nodes = solveDetails(levelForGeneration(request, payload), limit = 2, budget = 100_000).nodes
        }
        require(
            payload.clues.isNotEmpty() &&
                solveDetails(levelForGeneration(request, payload), limit = 2, budget = 100_000).solutions.size == 1,
        ) {
            "Could not generate a unique placement puzzle"
        }
        val level = levelForGeneration(request, payload)
        return GenerationResult(level, solution, PlacementGeneratorMetrics(size, payload.clues.size, nodes))
    }

    private fun generateLudeniContinuation(request: GenerationRequest): GenerationResult<PlacementLevel, PlacementWitness, PlacementGeneratorMetrics> {
        val size = ((request.parameters["size"] as? JsonPrimitive)?.intOrNull ?: 5).coerceIn(4, 5)
        val stage = ((request.parameters["stage"] as? JsonPrimitive)?.intOrNull ?: 0).coerceIn(0, 4)
        val random = ContinuationRandom(request.seed)
        val ids = listOf("ginger", "black", "white", "gray", "cream").take(size)
        val entities = ids.map(::PlacementEntity)
        val columns = random.shuffled((0 until size).toList())
        val rows = random.shuffled((0 until size).toList())
        val solution = PlacementState(ids.mapIndexed { index, id -> id to rows[index] * size + columns[index] }.toMap())
        val zoneIds = listOf("kitchen", "living-room", "bedroom", "study", "hall")
        val zones = MutableList(size) { row -> PlacementZone(zoneIds[row], MutableList(size) { column -> row * size + column }) }
        repeat(size - 1) { index ->
            val column = if (random.nextDouble() < .5) 0 else size - 1
            val cell = index * size + column
            if (index % 2 == 0 && cell !in solution.placements.values) {
                zones[index] = zones[index].copy(cells = zones[index].cells.filterNot { it == cell })
                zones[index + 1] = zones[index + 1].copy(cells = zones[index + 1].cells + cell)
            }
        }
        val featureIds = listOf("window", "armchair", "bed", "bowl", "plant")
        val features = zones.mapIndexed { index, zone -> PlacementFeature(featureIds[index], random.shuffled(zone.cells.filterNot { it in solution.placements.values }).first()) }
        var payload = PlacementPayload(size, entities, zones, features, emptyList())
        val candidates = mutableListOf<PlacementClue>()
        entities.forEach { first ->
            entities.filter { it != first }.forEach { second ->
                candidates += PlacementClue("leftOf", first.id, other = second.id)
                candidates += PlacementClue("above", first.id, other = second.id)
            }
            zones.forEach { zone -> candidates += PlacementClue("inZone", first.id, zone = zone.id); candidates += PlacementClue("notInZone", first.id, zone = zone.id) }
            features.forEach { feature -> candidates += PlacementClue("adjacentToFeature", first.id, feature = feature.id); candidates += PlacementClue("notAdjacentToFeature", first.id, feature = feature.id) }
        }
        val clues = entities.flatMap { entity ->
            val cell = solution.placements.getValue(entity.id)!!
            listOf(PlacementClue("inRow", entity.id, value = cell / size), PlacementClue("inColumn", entity.id, value = cell % size))
        }.toMutableList()
        payload = payload.copy(clues = clues)
        clues += random.shuffled(candidates.filter { clueStatus(payload, solution, it) == "satisfied" }).take(size * 3)
        payload = payload.copy(clues = clues)
        var removed = 0
        val removalOrder = payload.clues.filter { it.value != null } + random.shuffled(payload.clues.filter { it.value == null })
        for (clue in removalOrder) {
            if (removed >= 16 + stage * 2) break
            val reduced = payload.copy(clues = payload.clues.filterNot { it === clue })
            val result = solveDetails(levelForGeneration(request, reduced), limit = 2, budget = 5_000)
            if (!result.exceeded && result.solutions.size == 1) { payload = reduced; removed += 1 }
        }
        val level = levelForGeneration(request, payload)
        val result = solveDetails(level, limit = 2, budget = 100_000)
        require(!result.exceeded && result.solutions.size == 1) { "Unverified cat puzzle" }
        return GenerationResult(level, result.solutions.first(), PlacementGeneratorMetrics(size, payload.clues.size, result.nodes))
    }

    override fun solve(level: PlacementLevel): PlacementWitness? = solveDetails(level, limit = 1, budget = 100_000).solutions.firstOrNull()

    override fun hint(
        level: PlacementLevel,
        state: PlacementState,
        used: Int,
    ): PuzzleHint? {
        val result = solveDetails(level, limit = 2, budget = 100_000)
        if (result.exceeded || result.solutions.size != 1) return null
        val answer = result.solutions.first()
        val remaining = level.payload.entities.filter { state.placements[it.id] != answer.placements[it.id] }
        if (remaining.isEmpty()) return null
        val entity = if (used < 4) remaining.first() else remaining[(used - 4) % remaining.size]
        val cell = answer.placements.getValue(entity.id) ?: return null
        val data = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "entity" to JsonPrimitive(entity.id),
            "cell" to JsonPrimitive(cell),
            "row" to JsonPrimitive(cell / level.payload.size),
            "column" to JsonPrimitive(cell % level.payload.size),
        )
        zoneAt(level.payload, cell)?.let { data["zone"] = JsonPrimitive(it) }
        val nearest = level.payload.features.minByOrNull { distance(level.payload.size, cell, it.cell) }
        nearest?.let { data["feature"] = JsonPrimitive(it.id) }
        return PuzzleHint(
            kind = when (used) {
                0 -> "zone"
                1 -> "column"
                2 -> "landmark"
                3 -> "relative-cell"
                else -> "exact-cell"
            },
            data = JsonObject(data),
        )
    }

    fun inspect(
        level: PlacementLevel,
        state: PlacementState,
    ) = PlacementInspection(
        clueStatuses = level.payload.clues.map { clueStatus(level.payload, state, it) },
        conflicts = conflicts(level.payload, state).sorted(),
    )

    fun solveDetails(
        level: PlacementLevel,
        partial: PlacementState = initialState(level),
        limit: Int = 2,
        budget: Int = 100_000,
    ): PlacementSolveResult {
        if (!validateState(level, partial).valid) return PlacementSolveResult(emptyList(), 0, false)
        val solutions = mutableListOf<PlacementState>()
        var nodes = 0
        var exceeded = false
        fun visit(state: PlacementState) {
            if (solutions.size >= limit || exceeded) return
            nodes += 1
            if (nodes > budget) {
                exceeded = true
                return
            }
            if (conflicts(level.payload, state).isNotEmpty() || level.payload.clues.any { clueStatus(level.payload, state, it) == "violated" }) return
            val entity = level.payload.entities.firstOrNull { state.placements[it.id] == null }
            if (entity == null) {
                solutions += state
                return
            }
            for (cell in 0 until level.payload.size * level.payload.size) {
                if (featureAt(level.payload, cell) != null || cell in state.placements.values.filterNotNull()) continue
                val next = transition(level, state, PlacementAction("place", entity.id, cell))
                if (next.accepted && next.changed) visit(next.state)
                if (solutions.size >= limit || exceeded) break
            }
        }
        visit(partial)
        return PlacementSolveResult(solutions, nodes, exceeded)
    }

    fun clueStatus(
        payload: PlacementPayload,
        state: PlacementState,
        clue: PlacementClue,
    ): String {
        val first = state.placements[clue.entity] ?: return "unresolved"
        val row = first / payload.size
        val column = first % payload.size
        val matches = when (clue.type) {
            "inRow" -> row == clue.value
            "inColumn" -> column == clue.value
            "leftOf", "rightOf", "above", "below" -> {
                val second = state.placements[clue.other] ?: return "unresolved"
                when (clue.type) {
                    "leftOf" -> column < second % payload.size
                    "rightOf" -> column > second % payload.size
                    "above" -> row < second / payload.size
                    else -> row > second / payload.size
                }
            }
            "inZone" -> zoneAt(payload, first) == clue.zone
            "notInZone" -> zoneAt(payload, first) != clue.zone
            "adjacentToFeature", "notAdjacentToFeature" -> {
                val feature = payload.features.first { it.id == clue.feature }
                val adjacent = distance(payload.size, first, feature.cell) == 1
                if (clue.type == "adjacentToFeature") adjacent else !adjacent
            }
            else -> false
        }
        return if (matches) "satisfied" else "violated"
    }

    fun conflicts(
        payload: PlacementPayload,
        state: PlacementState,
    ): Set<String> {
        val groups = mutableMapOf<String, MutableList<String>>()
        state.placements.forEach { (entity, cell) ->
            if (cell == null) return@forEach
            val keys = mutableListOf("row:${cell / payload.size}", "column:${cell % payload.size}")
            zoneAt(payload, cell)?.let { keys += "zone:$it" }
            keys.forEach { groups.getOrPut(it) { mutableListOf() } += entity }
        }
        return groups.values.filter { it.size > 1 }.flatten().toSet()
    }

    private fun levelForGeneration(
        request: GenerationRequest,
        payload: PlacementPayload,
    ) = PuzzleLevel(
        identity =
            PuzzleIdentity(
                mechanicId = manifest.mechanicId,
                productGameId = manifest.mechanicId,
                levelPackId = "generated",
                levelId = "generated-${stableHash("v$PLACEMENT_GENERATOR_VERSION/${request.seed}/${payload.size}").toString(16)}",
                schemaVersion = 1,
                rulesVersion = PLACEMENT_RULES_VERSION,
                contentVersion = 1,
                generatorVersion = PLACEMENT_GENERATOR_VERSION,
                seed = request.seed,
            ),
        difficulty = request.difficulty,
        payload = payload,
    )
}

private fun validClue(
    payload: PlacementPayload,
    clue: PlacementClue,
): Boolean {
    if (payload.entities.none { it.id == clue.entity }) return false
    return when (clue.type) {
        "inRow", "inColumn" -> clue.value in 0 until payload.size
        "leftOf", "rightOf", "above", "below" -> clue.other != clue.entity && payload.entities.any { it.id == clue.other }
        "inZone", "notInZone" -> payload.zones.any { it.id == clue.zone }
        "adjacentToFeature", "notAdjacentToFeature" -> payload.features.any { it.id == clue.feature }
        else -> false
    }
}

private fun zoneAt(
    payload: PlacementPayload,
    cell: Int,
) = payload.zones.firstOrNull { cell in it.cells }?.id

private fun featureAt(
    payload: PlacementPayload,
    cell: Int,
) = payload.features.firstOrNull { it.cell == cell }

private fun distance(
    size: Int,
    first: Int,
    second: Int,
) = kotlin.math.abs(first / size - second / size) + kotlin.math.abs(first % size - second % size)

private fun issue(
    code: String,
    path: String,
) = ValidationIssue(code, path)

private fun rejected(
    state: PlacementState,
    reason: String,
) = Transition(false, false, state, reason)

private fun accepted(
    state: PlacementState,
    event: String,
    entity: String,
) = Transition(
    accepted = true,
    changed = true,
    state = state,
    events = listOf(EngineEvent(event, JsonObject(mapOf("entity" to JsonPrimitive(entity))))),
)
