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
import dev.smallwins.shared.ShikakuEngine
import dev.smallwins.shared.ShikakuLevel

@Composable
internal fun ShikakuGame(
    level: ShikakuLevel,
    generatedSeed: String?,
    portfolio: MobilePortfolioState,
    onBack: () -> Unit,
    showNext: Boolean,
    onNext: () -> Unit,
) {
    val session = rememberGameSession(
        portfolio, "shikaku", level.id, generatedSeed, ShikakuEngine.initial(level),
        ::encodeShikaku, { decodeShikaku(it, level) },
    )
    val state = session.state
    var anchor by remember(level.id) { mutableStateOf<Int?>(null) }
    var feedback by remember(level.id) { mutableStateOf<String?>(null) }
    val solved = ShikakuEngine.solved(level, state)
    val filled = state.regions.count { it >= 0 }
    val palette = listOf(Color(0xFFF5DDA9), Color(0xFFDDF3DF), Color(0xFFD9EEF5), Color(0xFFE7E3F3))

    GamePage("Shikaku", level.title, "$filled / ${state.regions.size} squares", onBack) {
        Text("Tap two opposite corners of a rectangle. Its area must match the one clue inside it.", color = MutedInk)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(level.rows) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(level.cols) { column ->
                        val cell = row * level.cols + column
                        val region = state.regions[cell]
                        val clue = level.clues.firstOrNull { it.cell == cell }
                        val selected = anchor == cell
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(if (region >= 0) palette[region % palette.size] else Color.White, RoundedCornerShape(14.dp))
                                .border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFF7F65A8) else Color(0x33202624), RoundedCornerShape(14.dp))
                                .semantics {
                                    contentDescription = "Row ${row + 1}, column ${column + 1}" +
                                        (clue?.let { ", clue ${it.area}" } ?: "")
                                }
                                .clickable(enabled = !solved) {
                                    when {
                                        anchor == null && region >= 0 -> {
                                            session.play(ShikakuEngine.clear(state, cell))
                                            feedback = "Rectangle cleared."
                                        }
                                        anchor == null -> {
                                            anchor = cell
                                            feedback = "Choose the opposite corner."
                                        }
                                        else -> {
                                            val first = anchor ?: cell
                                            runCatching { ShikakuEngine.place(level, state, first, cell) }
                                                .onSuccess { session.play(it); feedback = null }
                                                .onFailure { feedback = it.message }
                                            anchor = null
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (clue != null) Text("${clue.area}", fontWeight = FontWeight.Bold, color = Ink)
                        }
                    }
                }
            }
        }
        Text(feedback ?: "Tap a completed rectangle to clear it.", color = if (feedback?.startsWith("Every") == true || feedback?.startsWith("The") == true || feedback?.startsWith("Regions") == true) Color(0xFFA45143) else MutedInk)
        GameActions(session, solved) { current, used -> ShikakuEngine.hint(level, current, used) }
        if (solved) CompletionCard("Every square has a tidy home.", showNext, onNext)
    }
    RecordCompletion(portfolio, "shikaku", level.id, solved, session)
}
