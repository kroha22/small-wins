package dev.smallwins.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val Ink = Color(0xFF202624)
internal val MutedInk = Color(0xFF625B52)
internal val Success = Color(0xFFDDF3DF)

@Composable
internal fun GamePage(
    gameName: String,
    levelTitle: String,
    progress: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBack) { Text("Back") }
            Text(progress, fontWeight = FontWeight.SemiBold, color = MutedInk)
        }
        Text(gameName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text(levelTitle, style = MaterialTheme.typography.titleMedium, color = MutedInk)
        content()
    }
}

@Composable
internal fun CompletionCard(message: String, showNext: Boolean, onNext: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Success)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (showNext) Button(onClick = onNext) { Text("Next level") }
        }
    }
}

@Composable
internal fun <S> GameActions(
    session: GameSessionState<S>,
    solved: Boolean,
    hintProvider: ((S, Int) -> String?)? = null,
) where S : Any {
    session.hint?.let { text ->
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5DDA9))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Hint", fontWeight = FontWeight.Bold)
                Text(text, color = Ink)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(modifier = Modifier.weight(1f), enabled = session.history.isNotEmpty() && !solved, onClick = session::undo) {
            Text("Undo")
        }
        Button(modifier = Modifier.weight(1f), onClick = session::restart) { Text("Restart") }
        if (hintProvider != null) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = !solved,
                onClick = { session.requestHint(hintProvider) },
            ) { Text("Hint") }
        }
    }
}

@Composable
internal fun <S : Any> RecordCompletion(
    portfolio: MobilePortfolioState,
    gameId: String,
    levelId: String,
    solved: Boolean,
    session: GameSessionState<S>,
) {
    LaunchedEffect(solved, gameId, levelId) {
        if (solved) portfolio.complete(gameId, levelId, session.moveCount, session.hintsUsed > 0)
    }
}
