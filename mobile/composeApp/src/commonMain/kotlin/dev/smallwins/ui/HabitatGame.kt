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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import dev.smallwins.shared.HabitatEngine
import dev.smallwins.shared.HabitatLevel
import dev.smallwins.shared.HabitatMark

private enum class SurveyMode { OBSERVE, NOTE }

@Composable
internal fun HabitatGame(
    level: HabitatLevel,
    generatedSeed: String?,
    portfolio: MobilePortfolioState,
    onBack: () -> Unit,
    showNext: Boolean,
    onNext: () -> Unit,
) {
    val session = rememberGameSession(
        portfolio, "wildlife-survey", level.id, generatedSeed,
        HabitatSessionState(HabitatEngine.initial(level), emptySet()),
        ::encodeHabitat, { decodeHabitat(it, level) },
    )
    val state = session.state.game
    var mode by remember(level.id) { mutableStateOf(SurveyMode.OBSERVE) }
    val notes = session.state.notes
    val found = HabitatEngine.foundCount(state)
    val solved = HabitatEngine.solved(level, state)
    val speciesAt = remember(level.id) {
        buildMap {
            var offset = 0
            level.groups.forEach { group ->
                level.occupied.drop(offset).take(group.length).forEach { put(it, group.species) }
                offset += group.length
            }
        }
    }

    GamePage("Habitat Search", level.title, "$found / ${level.occupied.size} found", onBack) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD9EEF5))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(level.theme, fontWeight = FontWeight.Bold)
                Text("Counts show how many occupied squares belong in each row and column.", color = MutedInk)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == SurveyMode.OBSERVE, onClick = { mode = SurveyMode.OBSERVE }, label = { Text("Observe") })
                    FilterChip(selected = mode == SurveyMode.NOTE, onClick = { mode = SurveyMode.NOTE }, label = { Text("Note") })
                }
            }
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(0.55f))
                level.columnCounts.forEach { count ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("$count", fontWeight = FontWeight.Bold) }
                }
            }
            repeat(level.rows) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(0.55f), contentAlignment = Alignment.Center) { Text("${level.rowCounts[row]}", fontWeight = FontWeight.Bold) }
                    repeat(level.cols) { column ->
                        val cell = row * level.cols + column
                        val mark = state.marks[cell]
                        val noted = cell in notes
                        val label = when {
                            mark == HabitatMark.FOUND -> speciesAt[cell]?.take(1)?.uppercase() ?: "✓"
                            mark == HabitatMark.EMPTY -> "×"
                            noted -> "?"
                            else -> ""
                        }
                        val background = when (mark) {
                            HabitatMark.FOUND -> Color(0xFFDDF3DF)
                            HabitatMark.EMPTY -> Color(0xFFE7E3F3)
                            HabitatMark.UNKNOWN -> if (noted) Color(0xFFF5DDA9) else Color.White
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(background, RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0x33202624), RoundedCornerShape(12.dp))
                                .semantics { contentDescription = "Row ${row + 1}, column ${column + 1}, ${mark.name.lowercase()}" }
                                .clickable(enabled = !solved) {
                                    if (mode == SurveyMode.NOTE) {
                                        val nextNotes = if (cell in notes) notes - cell else notes + cell
                                        session.updateAuxiliary(session.state.copy(notes = nextNotes))
                                    } else {
                                        val nextNotes = notes - cell
                                        val markToApply = if (cell in level.occupied) HabitatMark.FOUND else HabitatMark.EMPTY
                                        val next = HabitatEngine.mark(level, state, cell, markToApply)
                                        session.play(HabitatSessionState(next, nextNotes))
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(label, fontWeight = FontWeight.Bold, color = Ink) }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Survey groups", fontWeight = FontWeight.Bold)
                level.groups.forEach { group -> Text("${group.species.replaceFirstChar { it.uppercase() }} · ${group.length} squares", color = MutedInk) }
            }
        }
        Text("Observe reveals a square. Notes are reversible and do not change progress.", color = MutedInk)
        GameActions(session, solved)
        if (solved) CompletionCard("The habitat survey is complete.", showNext, onNext)
    }
    RecordCompletion(portfolio, "wildlife-survey", level.id, solved, session)
}
