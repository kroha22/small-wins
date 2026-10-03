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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import dev.smallwins.shared.CatId
import dev.smallwins.shared.ClueStatus
import dev.smallwins.shared.PurrdokuEngine
import dev.smallwins.shared.PurrdokuLevel

@Composable
internal fun PurrdokuGame(
    level: PurrdokuLevel,
    generatedSeed: String?,
    portfolio: MobilePortfolioState,
    onBack: () -> Unit,
    showNext: Boolean,
    onNext: () -> Unit,
) {
    val session = rememberGameSession(
        portfolio, "purrdoku", level.id, generatedSeed, PurrdokuEngine.initial(level),
        ::encodePurrdoku, { decodePurrdoku(it, level) },
    )
    val state = session.state
    var selectedCat by remember(level.id) { mutableStateOf(level.cats.first()) }
    val solved = PurrdokuEngine.solved(level, state)
    val placed = state.placements.values.count { it != null }
    val catColors = mapOf(
        CatId.GINGER to Color(0xFFE7963E),
        CatId.BLACK to Color(0xFF303737),
        CatId.WHITE to Color(0xFFF7F3EA),
        CatId.GRAY to Color(0xFF9B9994),
    )

    GamePage("Purrdoku", level.title, "$placed / ${level.cats.size} cats", onBack) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5DDA9))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose a cat", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                level.cats.chunked(2).forEach { rowCats ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowCats.forEach { cat ->
                            FilterChip(
                                selected = selectedCat == cat,
                                onClick = { selectedCat = cat },
                                label = { Text(cat.label.removeSuffix(" cat")) },
                                leadingIcon = {
                                    Box(
                                        Modifier.size(20.dp).background(catColors.getValue(cat), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(cat.mark, color = if (cat == CatId.BLACK) Color.White else Ink, fontWeight = FontWeight.Bold) }
                                },
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(level.size) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(level.size) { column ->
                        val cell = row * level.size + column
                        val zone = level.zones.indexOfFirst { cell in it.cells }.coerceAtLeast(0)
                        val feature = level.features.firstOrNull { it.cell == cell }
                        val cat = state.placements.entries.firstOrNull { it.value == cell }?.key
                        val zoneColors = listOf(Color(0xFFFFF3D6), Color(0xFFE4F1ED), Color(0xFFE7E3F3))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(zoneColors[zone % zoneColors.size], RoundedCornerShape(18.dp))
                                .border(1.dp, Color(0x33202624), RoundedCornerShape(18.dp))
                                .semantics {
                                    contentDescription = "Row ${row + 1}, column ${column + 1}, " +
                                        (feature?.label ?: cat?.label ?: "empty")
                                }
                                .clickable(enabled = !solved) {
                                    if (feature == null) session.play(PurrdokuEngine.place(level, state, selectedCat, cell))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                cat != null -> Box(
                                    Modifier.size(50.dp).background(catColors.getValue(cat), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Text(cat.mark, color = if (cat == CatId.BLACK) Color.White else Ink, fontWeight = FontWeight.Bold) }
                                feature != null -> Text(feature.label.take(2).uppercase(), color = MutedInk, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Clues", fontWeight = FontWeight.Bold)
                level.clues.forEach { clue ->
                    val status = PurrdokuEngine.status(level, state, clue)
                    Text(
                        text = "${when (status) { ClueStatus.SATISFIED -> "✓"; ClueStatus.VIOLATED -> "!"; ClueStatus.UNRESOLVED -> "○" }}  ${PurrdokuEngine.clueText(level, clue)}",
                        color = when (status) {
                            ClueStatus.SATISFIED -> Color(0xFF2F7D58)
                            ClueStatus.VIOLATED -> Color(0xFFA45143)
                            ClueStatus.UNRESOLVED -> MutedInk
                        },
                    )
                }
            }
        }
        Text("Each cat needs its own row, column, and room. Tap a cat, then choose a free square.", color = MutedInk)
        GameActions(session, solved) { current, used -> PurrdokuEngine.hint(level, current, used) }
        if (solved) CompletionCard("Every cat found a cosy spot.", showNext, onNext)
    }
    RecordCompletion(portfolio, "purrdoku", level.id, solved, session)
}
