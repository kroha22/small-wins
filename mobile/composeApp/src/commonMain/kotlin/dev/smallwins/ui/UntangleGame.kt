package dev.smallwins.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.smallwins.shared.PuzzlePoint
import dev.smallwins.shared.UntangleEngine
import dev.smallwins.shared.UntangleLevel
import kotlin.math.hypot

@Composable
internal fun UntangleGame(
    level: UntangleLevel,
    generatedSeed: String?,
    portfolio: MobilePortfolioState,
    onBack: () -> Unit,
    showNext: Boolean,
    onNext: () -> Unit,
) {
    val session = rememberGameSession(
        portfolio, "untangle", level.id, generatedSeed, UntangleEngine.initial(level),
        ::encodeUntangle, { decodeUntangle(it, level) },
    )
    val state = session.state
    var draggingNode by remember(level.id) { mutableStateOf<String?>(null) }
    var feedback by remember(level.id) { mutableStateOf<String?>(null) }
    val conflicts = UntangleEngine.conflictEdges(level, state)
    val solved = UntangleEngine.solved(level, state)

    GamePage("Untangle", level.title, "${conflicts.size} crossed lines", onBack) {
        Text("Drag the dots until every line has a little room.", color = MutedInk)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFFE7E3F3), RoundedCornerShape(24.dp))
                .padding(14.dp)
                .semantics { contentDescription = "Untangle board with ${conflicts.size} crossed lines" }
                .pointerInput(level.id) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            draggingNode = level.nodes
                                .filterNot { it.fixed }
                                .minByOrNull { node ->
                                    val point = session.state.positions.getValue(node.id)
                                    hypot(
                                        (offset.x - point.x / 100f * size.width).toDouble(),
                                        (offset.y - point.y / 100f * size.height).toDouble(),
                                    )
                                }
                                ?.takeIf { node ->
                                    val point = session.state.positions.getValue(node.id)
                                    hypot(
                                        (offset.x - point.x / 100f * size.width).toDouble(),
                                        (offset.y - point.y / 100f * size.height).toDouble(),
                                    ) <= 42.0
                                }?.id
                            if (draggingNode != null) session.beginGesture()
                        },
                        onDragEnd = { session.endGesture(); draggingNode = null },
                        onDragCancel = { session.cancelGesture(); draggingNode = null },
                    ) { change, _ ->
                        draggingNode?.let { nodeId ->
                            val point = PuzzlePoint(
                                (change.position.x / size.width * 100f).coerceIn(level.minimum, level.maximum),
                                (change.position.y / size.height * 100f).coerceIn(level.minimum, level.maximum),
                            )
                            runCatching { UntangleEngine.move(level, session.state, nodeId, point) }
                                .onSuccess { session.updateGesture(it); feedback = null }
                                .onFailure { feedback = it.message }
                            change.consume()
                        }
                    }
                },
        ) {
            level.edges.forEachIndexed { index, edge ->
                val first = state.positions.getValue(edge.first)
                val second = state.positions.getValue(edge.second)
                drawLine(
                    color = if (index in conflicts) Color(0xFFC76558) else Color(0xFF657A73),
                    start = Offset(first.x / 100f * size.width, first.y / 100f * size.height),
                    end = Offset(second.x / 100f * size.width, second.y / 100f * size.height),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round,
                )
            }
            level.nodes.forEach { node ->
                val point = state.positions.getValue(node.id)
                val centre = Offset(point.x / 100f * size.width, point.y / 100f * size.height)
                drawCircle(if (node.fixed) Color(0xFF8E8A86) else Color.White, 24f, centre)
                drawCircle(if (node.id == draggingNode) Color(0xFF7F65A8) else Ink, 12f, centre)
            }
        }
        Text(
            feedback ?: if (solved) "No lines cross." else "Red lines still overlap. Move any dark-centred dot.",
            color = if (feedback == null) MutedInk else Color(0xFFA45143),
            fontWeight = if (solved) FontWeight.Bold else FontWeight.Normal,
        )
        GameActions(session, solved)
        if (solved) CompletionCard("The tangle has opened up.", showNext, onNext)
    }
    RecordCompletion(portfolio, "untangle", level.id, solved, session)
}
