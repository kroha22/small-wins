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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.smallwins.shared.NativeStatus
import dev.smallwins.shared.PipesEngine
import dev.smallwins.shared.PipesLevel
import dev.smallwins.shared.PipesState
import dev.smallwins.shared.pipesLevelOne
import dev.smallwins.shared.pipesTutorial
import dev.smallwins.shared.portfolioGames

private sealed interface Screen {
    data object Catalog : Screen
    data class Pipes(val level: PipesLevel) : Screen
}

@Composable
fun SmallWinsRoot() {
    var screen: Screen by remember { mutableStateOf(Screen.Catalog) }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8F5EF)) {
            when (val current = screen) {
                Screen.Catalog -> PortfolioCatalog(onOpenPipes = { screen = Screen.Pipes(pipesTutorial) })
                is Screen.Pipes -> PipesGame(
                    level = current.level,
                    onBack = { screen = Screen.Catalog },
                    onNext = { screen = Screen.Pipes(pipesLevelOne) },
                )
            }
        }
    }
}

@Composable
private fun PortfolioCatalog(onOpenPipes: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Small Wins", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("A cross-platform puzzle portfolio", color = Color(0xFF625B52))
            Spacer(Modifier.height(12.dp))
        }
        items(portfolioGames, key = { it.id }) { game ->
            val playable = game.nativeStatus == NativeStatus.PLAYABLE
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (playable) Modifier.clickable(onClick = onOpenPipes) else Modifier),
                colors = CardDefaults.cardColors(containerColor = if (playable) Color(0xFFE4F1ED) else Color.White),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(game.eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B756D))
                    Text(game.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(game.description, color = Color(0xFF4D4943))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(game.tags, style = MaterialTheme.typography.labelMedium)
                        Text(if (playable) "PLAY" else "WEB", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PipesGame(level: PipesLevel, onBack: () -> Unit, onNext: () -> Unit) {
    var state by remember(level.id) { mutableStateOf(PipesEngine.initial(level)) }
    val connections = PipesEngine.connections(level, state)
    val solved = PipesEngine.solved(level, state)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Text("${connections.reached.size} / ${level.ports.size} connected", fontWeight = FontWeight.SemiBold)
        }
        Text("Burrow Network", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(level.title, color = Color(0xFF625B52))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(level.rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(level.cols) { col ->
                        val cell = row * level.cols + col
                        PipeCell(
                            level = level,
                            state = state,
                            cell = cell,
                            connected = cell in connections.reached,
                            enabled = !solved,
                            onRotate = { state = PipesEngine.rotate(level, state, cell) },
                        )
                    }
                }
            }
        }
        Text("Tap a tunnel piece to turn it. Blue pieces are connected to the source.")
        if (solved) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFDDF3DF))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Everything connects.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (level.id == pipesTutorial.id) Button(onClick = onNext) { Text("Next level") }
                }
            }
        }
    }
}

@Composable
private fun PipeCell(
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
        modifier = Modifier
            .size(88.dp)
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
