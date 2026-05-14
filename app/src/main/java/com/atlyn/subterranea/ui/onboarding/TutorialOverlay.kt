package com.atlyn.subterranea.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase O-1: first-run coachmark tutorial.
 *
 * Four overlay cards that walk a brand-new player through the systems most
 * likely to confuse them, derived directly from the K-2 narrative report
 * frictions:
 *  1. The X|Y dice-trigger numbers on each tile.
 *  2. The zone badges (Safe / Moderate / Risky / Dangerous).
 *  3. The action budget (2 actions/turn, 1 on Nightmare).
 *  4. The build-menu deficit hints ("Need 1 more Crystal").
 *
 * Tapping "Next" advances; "Skip" dismisses the whole tutorial. Either path
 * marks the tutorial seen so it never reappears for this user. The Android
 * back button advances rather than dismissing so a player can't accidentally
 * skip it with a stray BACK press.
 */
@Composable
fun TutorialOverlay(
    onComplete: () -> Unit
) {
    val steps = remember {
        listOf(
            TutorialStep(
                title = "Tile dice triggers",
                emoji = "🎲",
                body = "Every revealed tile shows one or two numbers like 5 or 9|10. " +
                    "When the dice add up to that number, the tile produces its resource. " +
                    "Numbers in red (6 / 8) are the most likely rolls."
            ),
            TutorialStep(
                title = "Zone risk badges",
                emoji = "🛡️",
                body = "The icon at the top of an unrevealed tile tells you the danger level: " +
                    "Safe, Moderate, Risky, or Dangerous. Riskier tiles can have hazards " +
                    "but also better rewards. Pinch to zoom in for a closer look."
            ),
            TutorialStep(
                title = "Action budget",
                emoji = "⚡",
                body = "You have 2 actions per turn (1 on Nightmare). Building, exploring, " +
                    "trading, and using a structure ability each cost 1 action. End your " +
                    "turn early any time — the dice will roll automatically next turn."
            ),
            TutorialStep(
                title = "Building structures",
                emoji = "🛠️",
                body = "Tap a tile, then Build. Structures you can't afford show a hint " +
                    "like \"Need 1 more Crystal\". Trade unwanted resources at the bottom-bar " +
                    "Trade button if you're missing what you need."
            )
        )
    }

    var index by remember { mutableStateOf(0) }
    val current = steps[index]
    val isLast = index == steps.lastIndex

    BackHandler(enabled = true) {
        if (isLast) onComplete() else index += 1
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            // Consume background taps so they don't fall through to the game.
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 380.dp)
                .border(1.dp, Color(0x6629B6F6), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE111827))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    current.emoji,
                    fontSize = 40.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    current.title,
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    current.body,
                    color = Color(0xFFCFD8DC),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(18.dp))

                // Dot pager showing position
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (i == index) 22.dp else 8.dp)
                                .background(
                                    if (i == index) Color(0xFF29B6F6) else Color(0x55FFFFFF),
                                    if (i == index) RoundedCornerShape(4.dp) else CircleShape
                                )
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onComplete,
                        modifier = Modifier.semantics { contentDescription = "Skip the tutorial" }
                    ) {
                        Text("Skip", color = Color(0xFFB0BEC5), fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            if (isLast) onComplete() else index += 1
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6)),
                        modifier = Modifier.semantics {
                            contentDescription = if (isLast) "Finish tutorial" else "Next tutorial step"
                        }
                    ) {
                        Text(
                            if (isLast) "Got it" else "Next",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

private data class TutorialStep(
    val title: String,
    val emoji: String,
    val body: String
)
