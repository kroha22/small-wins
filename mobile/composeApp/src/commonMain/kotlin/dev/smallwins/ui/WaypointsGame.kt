package dev.smallwins.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.smallwins.shared.WaypointsEngine
import dev.smallwins.shared.WaypointsLevel

@Composable
internal fun WaypointsGame(
    level: WaypointsLevel,
    generatedSeed: String?,
    portfolio: MobilePortfolioState,
    onBack: () -> Unit,
    showNext: Boolean,
    onNext: () -> Unit,
) {
    val session = rememberGameSession(
        portfolio, "waypoints", level.id, generatedSeed, WaypointsEngine.initial(level),
        ::encodeWaypoints, { decodeWaypoints(it, level) },
    )
    val state = session.state
    var feedback by remember(level.id) { mutableStateOf<String?>(null) }
    val collected = level.waypoints.count { it in state.path }
    val solved = WaypointsEngine.solved(level, state)

    GamePage("Waypoints", level.title, "$collected / ${level.waypoints.size} carrots", onBack) {
        Text("Build one continuous path. Visit every carrot before the finish flag.", color = MutedInk)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(level.rows) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(level.cols) { column ->
                        val cell = row * level.cols + column
                        val step = state.path.indexOf(cell)
                        val blocked = cell in level.blocked
                        val end = state.path.last() == cell
                        val label = when {
                            cell == level.start -> "S"
                            cell == level.target -> "F"
                            cell in level.waypoints -> "C"
                            step >= 0 -> "${step + 1}"
                            else -> ""
                        }
                        val background = when {
                            blocked -> Color(0xFF827D75)
                            end -> Color(0xFFF5DDA9)
                            step >= 0 -> Color(0xFFDDF3DF)
                            else -> Color.White
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(background, RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0x33202624), RoundedCornerShape(16.dp))
                                .semantics {
                                    contentDescription = when {
                                        blocked -> "Blocked cell"
                                        cell == level.start -> "Start"
                                        cell == level.target -> "Finish"
                                        cell in level.waypoints -> "Carrot"
                                        label.isNotEmpty() -> "Path step $label"
                                        else -> "Open cell"
                                    }
                                }
                                .clickable(enabled = !blocked && !solved) {
                                    runCatching { WaypointsEngine.add(level, state, cell) }
                                        .onSuccess { session.play(it); feedback = null }
                                        .onFailure { feedback = it.message }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (blocked) Color.White else Ink,
                            )
                        }
                    }
                }
            }
        }
        Text(
            feedback ?: "Tap the next neighbouring square.",
            color = if (feedback == null) MutedInk else Color(0xFFA45143),
        )
        GameActions(session, solved) { current, used -> WaypointsEngine.hint(level, current, used) }
        if (solved) CompletionCard("The scenic route is complete.", showNext, onNext)
    }
    RecordCompletion(portfolio, "waypoints", level.id, solved, session)
}
