package dev.smallwins.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.smallwins.shared.NativeStatus
import dev.smallwins.shared.LevelGenerator
import dev.smallwins.shared.PurrdokuLevel
import dev.smallwins.shared.ShikakuLevel
import dev.smallwins.shared.UntangleLevel
import dev.smallwins.shared.WaypointsLevel
import dev.smallwins.shared.HabitatLevel
import dev.smallwins.shared.PipesEngine
import dev.smallwins.shared.PipesLevel
import dev.smallwins.shared.PipesState
import dev.smallwins.shared.pipesLevels
import dev.smallwins.shared.portfolioGames
import dev.smallwins.shared.purrdokuLevels
import dev.smallwins.shared.shikakuLevels
import dev.smallwins.shared.untangleLevels
import dev.smallwins.shared.waypointsLevels
import dev.smallwins.shared.habitatLevels

private sealed interface Screen {
    data object Catalog : Screen
    data object Settings : Screen
    data class Levels(val gameId: String) : Screen
    data class Pipes(val level: PipesLevel, val seed: String? = null) : Screen
    data class Purrdoku(val level: PurrdokuLevel, val seed: String? = null) : Screen
    data class Untangle(val level: UntangleLevel, val seed: String? = null) : Screen
    data class Waypoints(val level: WaypointsLevel, val seed: String? = null) : Screen
    data class Shikaku(val level: ShikakuLevel, val seed: String? = null) : Screen
    data class Habitat(val level: HabitatLevel, val seed: String? = null) : Screen
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun SmallWinsRoot() {
    val storage = rememberPlatformKeyValueStorage()
    val portfolio = remember(storage) { MobilePortfolioState(storage) }
    var screen: Screen by remember(portfolio) {
        mutableStateOf(portfolio.activeAttempt?.let(::screenForAttempt) ?: Screen.Catalog)
    }
    BackHandler(enabled = screen != Screen.Catalog) {
        screen = parentScreen(screen)
    }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8F5EF)) {
            when (val current = screen) {
                Screen.Catalog -> PortfolioCatalog(
                    portfolio = portfolio,
                    onOpenGame = { gameId -> screen = Screen.Levels(gameId) },
                    onSettings = { screen = Screen.Settings },
                    onResume = { portfolio.activeAttempt?.let(::screenForAttempt)?.let { screen = it } },
                )
                Screen.Settings -> SettingsPage(
                    portfolio = portfolio,
                    onBack = { screen = Screen.Catalog },
                    onClear = { portfolio.clearAll(); screen = Screen.Catalog },
                )
                is Screen.Levels -> LevelCatalog(
                    gameId = current.gameId,
                    portfolio = portfolio,
                    onBack = { screen = Screen.Catalog },
                    onOpen = { levelId -> screen = screenFor(current.gameId, levelId) },
                    onGenerate = { seed -> screen = generatedScreen(current.gameId, seed) },
                )
                is Screen.Pipes -> key("pipes/${current.level.id}") {
                    PipesGame(
                        level = current.level,
                        generatedSeed = current.seed,
                        portfolio = portfolio,
                        onBack = { screen = Screen.Levels("pipes") },
                        showNext = current.level in pipesLevels && current.level != pipesLevels.last(),
                        onNext = { screen = Screen.Pipes(pipesLevels[pipesLevels.indexOf(current.level) + 1]) },
                    )
                }
                is Screen.Purrdoku -> key("purrdoku/${current.level.id}") {
                    PurrdokuGame(
                        level = current.level,
                        generatedSeed = current.seed,
                        portfolio = portfolio,
                        onBack = { screen = Screen.Levels("purrdoku") },
                        showNext = current.level in purrdokuLevels && current.level != purrdokuLevels.last(),
                        onNext = { screen = Screen.Purrdoku(purrdokuLevels[purrdokuLevels.indexOf(current.level) + 1]) },
                    )
                }
                is Screen.Untangle -> key("untangle/${current.level.id}") {
                    UntangleGame(
                        level = current.level,
                        generatedSeed = current.seed,
                        portfolio = portfolio,
                        onBack = { screen = Screen.Levels("untangle") },
                        showNext = current.level in untangleLevels && current.level != untangleLevels.last(),
                        onNext = { screen = Screen.Untangle(untangleLevels[untangleLevels.indexOf(current.level) + 1]) },
                    )
                }
                is Screen.Waypoints -> key("waypoints/${current.level.id}") {
                    WaypointsGame(
                        level = current.level,
                        generatedSeed = current.seed,
                        portfolio = portfolio,
                        onBack = { screen = Screen.Levels("waypoints") },
                        showNext = current.level in waypointsLevels && current.level != waypointsLevels.last(),
                        onNext = { screen = Screen.Waypoints(waypointsLevels[waypointsLevels.indexOf(current.level) + 1]) },
                    )
                }
                is Screen.Shikaku -> key("shikaku/${current.level.id}") {
                    ShikakuGame(
                        level = current.level,
                        generatedSeed = current.seed,
                        portfolio = portfolio,
                        onBack = { screen = Screen.Levels("shikaku") },
                        showNext = current.level in shikakuLevels && current.level != shikakuLevels.last(),
                        onNext = { screen = Screen.Shikaku(shikakuLevels[shikakuLevels.indexOf(current.level) + 1]) },
                    )
                }
                is Screen.Habitat -> key("habitat/${current.level.id}") {
                    HabitatGame(
                        level = current.level,
                        generatedSeed = current.seed,
                        portfolio = portfolio,
                        onBack = { screen = Screen.Levels("wildlife-survey") },
                        showNext = current.level in habitatLevels && current.level != habitatLevels.last(),
                        onNext = { screen = Screen.Habitat(habitatLevels[habitatLevels.indexOf(current.level) + 1]) },
                    )
                }
            }
        }
    }
}

private fun parentScreen(screen: Screen): Screen = when (screen) {
    Screen.Catalog -> Screen.Catalog
    Screen.Settings, is Screen.Levels -> Screen.Catalog
    is Screen.Pipes -> Screen.Levels("pipes")
    is Screen.Purrdoku -> Screen.Levels("purrdoku")
    is Screen.Untangle -> Screen.Levels("untangle")
    is Screen.Waypoints -> Screen.Levels("waypoints")
    is Screen.Shikaku -> Screen.Levels("shikaku")
    is Screen.Habitat -> Screen.Levels("wildlife-survey")
}

private data class LevelItem(val id: String, val title: String, val difficulty: String)

private fun levelItems(gameId: String): List<LevelItem> {
    val levels = when (gameId) {
        "purrdoku" -> purrdokuLevels.map { it.id to it.title }
        "pipes" -> pipesLevels.map { it.id to it.title }
        "untangle" -> untangleLevels.map { it.id to it.title }
        "waypoints" -> waypointsLevels.map { it.id to it.title }
        "shikaku" -> shikakuLevels.map { it.id to it.title }
        "wildlife-survey" -> habitatLevels.map { it.id to it.title }
        else -> emptyList()
    }
    return levels.mapIndexed { index, (id, title) ->
        LevelItem(id, title, when (index) { 0 -> "Tutorial"; 1, 2 -> "Easy"; 3, 4 -> "Medium"; else -> "Hard" })
    }
}

private fun screenFor(gameId: String, levelId: String): Screen = when (gameId) {
    "purrdoku" -> Screen.Purrdoku(purrdokuLevels.first { it.id == levelId })
    "pipes" -> Screen.Pipes(pipesLevels.first { it.id == levelId })
    "untangle" -> Screen.Untangle(untangleLevels.first { it.id == levelId })
    "waypoints" -> Screen.Waypoints(waypointsLevels.first { it.id == levelId })
    "shikaku" -> Screen.Shikaku(shikakuLevels.first { it.id == levelId })
    "wildlife-survey" -> Screen.Habitat(habitatLevels.first { it.id == levelId })
    else -> Screen.Catalog
}

private fun generatedScreen(gameId: String, seed: String): Screen = when (gameId) {
    "purrdoku" -> Screen.Purrdoku(LevelGenerator.purrdoku(seed), seed)
    "pipes" -> Screen.Pipes(LevelGenerator.pipes(seed), seed)
    "untangle" -> Screen.Untangle(LevelGenerator.untangle(seed), seed)
    "waypoints" -> Screen.Waypoints(LevelGenerator.waypoints(seed), seed)
    "shikaku" -> Screen.Shikaku(LevelGenerator.shikaku(seed), seed)
    "wildlife-survey" -> Screen.Habitat(LevelGenerator.habitat(seed), seed)
    else -> Screen.Catalog
}

private fun screenForAttempt(attempt: SavedAttempt): Screen? {
    val screen = if (attempt.generatedSeed != null) {
        generatedScreen(attempt.gameId, attempt.generatedSeed)
    } else {
        runCatching { screenFor(attempt.gameId, attempt.levelId) }.getOrNull() ?: return null
    }
    val restoredId = when (screen) {
        is Screen.Pipes -> screen.level.id
        is Screen.Purrdoku -> screen.level.id
        is Screen.Untangle -> screen.level.id
        is Screen.Waypoints -> screen.level.id
        is Screen.Shikaku -> screen.level.id
        is Screen.Habitat -> screen.level.id
        else -> return null
    }
    return screen.takeIf { restoredId == attempt.levelId }
}

@Composable
private fun LevelCatalog(
    gameId: String,
    portfolio: MobilePortfolioState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onGenerate: (String) -> Unit,
) {
    val game = portfolioGames.first { it.id == gameId }
    var seed by remember(gameId) { mutableStateOf("small-wins") }
    LazyColumn(
        modifier = Modifier.testTag("level-catalog"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.height(16.dp))
            Text(game.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Seven web levels, shared natively on Android and iOS.", color = Color(0xFF625B52))
        }
        items(levelItems(gameId), key = { it.id }) { level ->
            val completed = portfolio.isComplete(gameId, level.id)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("level-${level.id}").clickable { onOpen(level.id) },
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(level.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(level.difficulty, color = Color(0xFF625B52))
                    }
                    Text(if (completed) "DONE" else "PLAY", fontWeight = FontWeight.Bold, color = if (completed) Color(0xFF2F7D58) else Ink)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE4F1ED))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fresh puzzle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("The same seed always recreates the same puzzle on Android and iOS.", color = Color(0xFF625B52))
                    OutlinedTextField(
                        value = seed,
                        onValueChange = { seed = it },
                        modifier = Modifier.fillMaxWidth().testTag("generator-seed"),
                        label = { Text("Seed") },
                        singleLine = true,
                    )
                    Button(
                        modifier = Modifier.testTag("generate-level"),
                        enabled = seed.isNotBlank(),
                        onClick = { onGenerate(seed.trim()) },
                    ) { Text("Generate") }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    portfolio: MobilePortfolioState,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = onBack) { Text("Back") }
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Your puzzles stay on this device. No account is needed.", color = MutedInk)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Less movement", fontWeight = FontWeight.Bold)
                    Text("Keep transitions gentle and simple.", color = MutedInk)
                }
                Switch(
                    checked = portfolio.settings.reduceMotion,
                    onCheckedChange = portfolio::setReduceMotion,
                    modifier = Modifier
                        .testTag("reduce-motion")
                        .semantics { contentDescription = "Less movement" },
                )
            }
        }
        Text(
            "The preference is stored locally. Mobile boards already avoid non-essential animation.",
            color = MutedInk,
        )
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE4F1ED))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Local progress", fontWeight = FontWeight.Bold)
                Text("${portfolio.completed.size} completed puzzles", color = MutedInk)
                Text(if (portfolio.activeAttempt == null) "No puzzle is waiting to resume." else "Your latest puzzle is ready to resume.", color = MutedInk)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFECE8))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A fresh notebook", fontWeight = FontWeight.Bold)
                Text("Clear completed puzzles, settings, and the latest attempt on this device.", color = MutedInk)
                if (confirmClear) {
                    Text("Clear all local progress? This cannot be undone.", color = Color(0xFFA45143))
                    Button(modifier = Modifier.testTag("confirm-clear-progress"), onClick = onClear) { Text("Yes, clear everything") }
                    Button(onClick = { confirmClear = false }) { Text("Keep my progress") }
                } else {
                    Button(modifier = Modifier.testTag("clear-progress"), onClick = { confirmClear = true }) { Text("Clear local progress") }
                }
            }
        }
    }
}

@Composable
private fun PortfolioCatalog(
    portfolio: MobilePortfolioState,
    onOpenGame: (String) -> Unit,
    onSettings: () -> Unit,
    onResume: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.testTag("game-catalog"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Small Wins", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Button(modifier = Modifier.testTag("settings"), onClick = onSettings) { Text("Settings") }
            }
            Text("A cross-platform puzzle portfolio", color = Color(0xFF625B52))
            Spacer(Modifier.height(12.dp))
        }
        portfolio.activeAttempt?.let { attempt ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("resume-attempt").clickable(onClick = onResume),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5DDA9)),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("CONTINUE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(portfolioGames.firstOrNull { it.id == attempt.gameId }?.name ?: "Latest puzzle", fontWeight = FontWeight.Bold)
                        Text("${attempt.moveCount} moves · ${attempt.hintsUsed} hints", color = MutedInk)
                    }
                }
            }
        }
        items(portfolioGames, key = { it.id }) { game ->
            val playable = game.nativeStatus == NativeStatus.PLAYABLE
            val completedLevels = levelItems(game.id).count { portfolio.isComplete(game.id, it.id) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("game-${game.id}")
                    .then(if (playable) Modifier.clickable { onOpenGame(game.id) } else Modifier),
                colors = CardDefaults.cardColors(containerColor = if (playable) Color(0xFFE4F1ED) else Color.White),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(game.eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B756D))
                    Text(game.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(game.description, color = Color(0xFF4D4943))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(game.tags, style = MaterialTheme.typography.labelMedium)
                        Text(if (playable) "$completedLevels / 7" else "WEB", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PipesGame(
    level: PipesLevel,
    generatedSeed: String?,
    portfolio: MobilePortfolioState,
    onBack: () -> Unit,
    showNext: Boolean,
    onNext: () -> Unit,
) {
    val session = rememberGameSession(
        portfolio, "pipes", level.id, generatedSeed, PipesEngine.initial(level),
        ::encodePipes, { decodePipes(it, level.ports.size) },
    )
    val state = session.state
    val connections = PipesEngine.connections(level, state)
    val solved = PipesEngine.solved(level, state)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pipes-screen-${level.id}")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(modifier = Modifier.testTag("back-to-levels"), onClick = onBack) { Text("Back") }
            Text(
                "${connections.reached.size} / ${level.ports.size} connected",
                modifier = Modifier.testTag("connection-progress"),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text("Burrow Network", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(level.title, color = Color(0xFF625B52))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(level.rows) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(level.cols) { col ->
                        val cell = row * level.cols + col
                        PipeCell(
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            level = level,
                            state = state,
                            cell = cell,
                            connected = cell in connections.reached,
                            enabled = !solved,
                            onRotate = { session.play(PipesEngine.rotate(level, state, cell)) },
                        )
                    }
                }
            }
        }
        Text("Tap a tunnel piece to turn it. Blue pieces are connected to the source.")
        GameActions(session, solved) { current, used -> PipesEngine.hint(level, current, used) }
        if (solved) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFDDF3DF))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Everything connects.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (showNext) {
                        Button(modifier = Modifier.testTag("next-level"), onClick = onNext) { Text("Next level") }
                    }
                }
            }
        }
    }
    RecordCompletion(portfolio, "pipes", level.id, solved, session)
}

@Composable
private fun PipeCell(
    modifier: Modifier = Modifier,
    level: PipesLevel,
    state: PipesState,
    cell: Int,
    connected: Boolean,
    enabled: Boolean,
    onRotate: () -> Unit,
) {
    val ports = PipesEngine.portsAt(level, state, cell)
    val background = if (connected) Color(0xFFD9EEF5) else Color.White
    val line = if (connected) Color(0xFF2584A4) else Color(0xFF716D67)
    Box(
        modifier = modifier
            .testTag("pipe-$cell")
            .background(background, RoundedCornerShape(18.dp))
            .semantics { contentDescription = "Pipe ${cell + 1}, ${if (connected) "connected" else "not connected"}" }
            .clickable(enabled = enabled, onClick = onRotate),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val centre = Offset(size.width / 2, size.height / 2)
            val ends = listOf(
                Offset(centre.x, 0f),
                Offset(size.width, centre.y),
                Offset(centre.x, size.height),
                Offset(0f, centre.y),
            )
            ports.forEach { direction ->
                drawLine(Color(0xFF2D3331), centre, ends[direction], strokeWidth = 18f, cap = StrokeCap.Round)
                drawLine(line, centre, ends[direction], strokeWidth = 11f, cap = StrokeCap.Round)
            }
            drawCircle(
                color = if (cell == level.sourceCell) Color(0xFF145D75) else line,
                radius = if (cell == level.sourceCell) 13f else 8f,
                center = centre,
                style = if (cell == level.sourceCell) Stroke(width = 6f) else androidx.compose.ui.graphics.drawscope.Fill,
            )
        }
    }
}
