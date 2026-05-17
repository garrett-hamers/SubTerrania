package com.atlyn.subterranea.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atlyn.subterranea.domain.model.*
import com.atlyn.subterranea.ui.animation.*
import com.atlyn.subterranea.ui.audio.GameSound
import com.atlyn.subterranea.ui.audio.SoundManager
import com.atlyn.subterranea.R
import com.atlyn.subterranea.ui.util.IconHelper
import com.atlyn.subterranea.ui.viewmodel.GameViewModel
import androidx.compose.ui.res.painterResource

@Composable
fun GameScreen(
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gameUIState by viewModel.gameUIState.collectAsState()
    val showTradeMenu by viewModel.showTradeMenu.collectAsState()
    val context = LocalContext.current
    
    // End turn confirmation dialog state
    var showEndTurnConfirm by remember { mutableStateOf(false) }

    // Event history dialog state — opened by tapping the bottom event ticker.
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Settings overlay state — opened by the gear icon in TopHUD.
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Sound manager - remember and handle lifecycle
    val soundManager = remember { SoundManager(context) }
    DisposableEffect(Unit) {
        onDispose { soundManager.release() }
    }
    
    // Listen for sound events from ViewModel
    val soundEvent by viewModel.soundEvent.collectAsState()
    LaunchedEffect(soundEvent) {
        soundEvent?.let { sound ->
            soundManager.play(sound)
            viewModel.clearSoundEvent()
        }
    }
    
    // Animation triggers
    val diceRollTrigger by viewModel.diceRollTrigger.collectAsState()
    val productionTrigger by viewModel.productionTrigger.collectAsState()
    val buildTrigger by viewModel.buildTrigger.collectAsState()
    
    // Dice shake animation state
    var isDiceShaking by remember { mutableStateOf(false) }
    LaunchedEffect(diceRollTrigger) {
        if (diceRollTrigger > 0) {
            isDiceShaking = true
            kotlinx.coroutines.delay(400)
            isDiceShaking = false
        }
    }
    
    // Production flash animation
    var showProductionFlash by remember { mutableStateOf(false) }
    LaunchedEffect(productionTrigger) {
        if (productionTrigger > 0) {
            showProductionFlash = true
            kotlinx.coroutines.delay(1500)
            showProductionFlash = false
        }
    }
    
    // Calculate explorable tiles (unrevealed tiles adjacent to revealed ones)
    val explorableTiles = if (uiState.canExploreThisTurn && 
                              uiState.turnPhase == TurnPhase.MAIN_ACTION &&
                              uiState.actionsThisTurn < uiState.maxActionsPerTurn) {
        uiState.board.keys.filter { coord ->
            val tile = uiState.board[coord]
            tile != null && !tile.isRevealed && coord.neighbors().any { neighbor ->
                uiState.board[neighbor]?.isRevealed == true
            }
        }.toSet()
    } else {
        emptySet()
    }
    
    // Calculate buildable tiles (revealed tiles without structures, not magma/bedrock)
    val buildableTiles = if (uiState.turnPhase == TurnPhase.MAIN_ACTION &&
                            uiState.actionsThisTurn < uiState.maxActionsPerTurn) {
        uiState.board.keys.filter { coord ->
            val tile = uiState.board[coord]
            tile != null && tile.isRevealed && 
            tile.terrain != TerrainType.MAGMA_FLOW && 
            tile.terrain != TerrainType.BEDROCK &&
            uiState.structures.none { it.location == coord }
        }.toSet()
    } else {
        emptySet()
    }

    val showExplorationModal = uiState.lastExplorationEvent != null &&
        uiState.lastExplorationEvent !is ExplorationEvent.StableGround
    val showConsolationModal = !showExplorationModal && uiState.pendingConsolation
    val showBuildModal = !showExplorationModal && !showConsolationModal && gameUIState.showBuildMenu
    val showTradeModal = !showExplorationModal && !showConsolationModal && !showBuildModal && showTradeMenu
    val hasHigherPriorityModal = showExplorationModal || showConsolationModal || showBuildModal || showTradeModal
    val showEndTurnDialog = !hasHigherPriorityModal && showEndTurnConfirm

    LaunchedEffect(hasHigherPriorityModal) {
        if (hasHigherPriorityModal && showEndTurnConfirm) {
            showEndTurnConfirm = false
        }
        if (hasHigherPriorityModal && showHistoryDialog) {
            showHistoryDialog = false
        }
        if (hasHigherPriorityModal && showSettingsDialog) {
            showSettingsDialog = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D1A),
                        Color(0xFF1A1A2E),
                        Color(0xFF0D0D1A)
                    )
                )
            )
    ) {
        // Only render game board when difficulty has been selected
        val showDifficultyMenu by viewModel.showDifficultyMenu.collectAsState()
        val metaProg by viewModel.metaProgression.collectAsState()
        if (!showDifficultyMenu) {
        // Main hex map
        HexMap(
            board = uiState.board,
            structures = uiState.structures,
            selectedTile = gameUIState.selectedTile,
            explorableTiles = explorableTiles,
            buildableTiles = buildableTiles,
            onTileClick = { coord -> viewModel.onTileClicked(coord) },
            modifier = Modifier.fillMaxSize().padding(top = 300.dp, bottom = 190.dp)
        )
        
        // Top HUD - Turn info and VP
        TopHUD(
            uiState = uiState,
            onSettings = { if (!hasHigherPriorityModal) showSettingsDialog = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        // Tutorial hint
        if (gameUIState.showTutorial) {
            uiState.getCurrentHint(
                showTutorial = gameUIState.showTutorial,
                selectedTile = gameUIState.selectedTile
            )?.let { hint ->
                TutorialHint(
                    hint = hint,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 50.dp)
                )
            }
        }
        
        // Resource bar
        ResourceBar(
            player = uiState.currentPlayer,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 148.dp, start = 6.dp, end = 6.dp)
        )
        
        // Dice display with production info
        if (uiState.lastDiceResult != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 236.dp)
                    .shakeAnimation(isDiceShaking),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DiceDisplay(
                    diceResult = uiState.lastDiceResult!!,
                    isAnimating = isDiceShaking,
                    modifier = Modifier
                )
                
                // Show production summary with flash animation
                if (uiState.lastProduction.isNotEmpty()) {
                    ProductionBadge(
                        production = uiState.lastProduction,
                        isAnimating = showProductionFlash,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        // Selected tile info panel
        if (gameUIState.selectedTile != null && !gameUIState.showBuildMenu) {
            val tile = uiState.board[gameUIState.selectedTile]
            if (tile != null) {
                SelectedTileInfo(
                    tile = tile,
                    structure = uiState.getStructureAt(gameUIState.selectedTile!!),
                    canExplore = !tile.isRevealed && uiState.canExploreThisTurn && 
                        uiState.turnPhase == TurnPhase.MAIN_ACTION,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 125.dp)
                )
            }
        }
        
        // Action buttons
        val availableStructures = remember(gameUIState.selectedTile, uiState.currentPlayer.resources) {
            if (gameUIState.selectedTile != null) viewModel.getAvailableStructures() else emptyList()
        }

        val usableAbilities = remember(uiState.structures, uiState.turnPhase, uiState.actionsThisTurn) {
            viewModel.getUsableAbilities()
        }
        var showAbilityMenu by remember { mutableStateOf(false) }

        ActionButtons(
            uiState = uiState,
            selectedTile = gameUIState.selectedTile,
            explorableTiles = explorableTiles,
            onRollDice = { viewModel.rollDice() },
            onEndTurn = {
                val actionsLeft = uiState.maxActionsPerTurn - uiState.actionsThisTurn
                if (actionsLeft > 0 && uiState.turnPhase == TurnPhase.MAIN_ACTION) {
                    if (!hasHigherPriorityModal) {
                        showEndTurnConfirm = true
                    }
                } else {
                    viewModel.endTurn()
                }
            },
            onBuildClick = { viewModel.toggleBuildMenu() },
            onExploreClick = { viewModel.exploreSelectedTile() },
            onClearRubble = { viewModel.clearRubble() },
            onTradeClick = { viewModel.toggleTradeMenu() },
            onAbilityClick = { if (usableAbilities.isNotEmpty()) showAbilityMenu = true },
            canTrade = viewModel.canTrade(),
            hasAvailableStructures = availableStructures.isNotEmpty(),
            usableAbilities = usableAbilities,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
        )

        if (showAbilityMenu) {
            BackHandler(enabled = true) { showAbilityMenu = false }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showAbilityMenu = false }
            )
            AbilityMenu(
                usableAbilities = usableAbilities,
                onUseAbility = { structure ->
                    viewModel.useStructureAbility(structure.location)
                    showAbilityMenu = false
                },
                onDismiss = { showAbilityMenu = false },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Build menu popup with backdrop
        if (showBuildModal) {
            BackHandler(enabled = true) { viewModel.closeBuildMenu() }
            // Dark backdrop that dismisses on tap
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { viewModel.closeBuildMenu() }
            )
            
            BuildMenu(
                availableStructures = if (gameUIState.selectedTile != null) viewModel.getAvailableStructures() else emptyList(),
                player = uiState.currentPlayer,
                selectedTileName = gameUIState.selectedTile?.let { coord ->
                    uiState.board[coord]?.terrain?.displayName()
                },
                onBuild = { type -> viewModel.buildStructure(type) },
                onDismiss = { viewModel.closeBuildMenu() },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Trade menu popup with backdrop
        if (showTradeModal) {
            BackHandler(enabled = true) { viewModel.closeTradeMenu() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { viewModel.closeTradeMenu() }
            )
            
            TradeMenu(
                tradableResources = viewModel.getTradableResources(),
                player = uiState.currentPlayer,
                tradeRatio = if (uiState.discountTradeAvailable) 2 else uiState.difficulty.tradeRatio,
                onTrade = { give, receive -> viewModel.tradeResources(give, receive) },
                onDismiss = { viewModel.closeTradeMenu() },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Event ticker (bottom strip) - one line, taps to open full history modal.
        EventTicker(
            events = uiState.eventLog,
            onClick = { if (!hasHigherPriorityModal && !showEndTurnConfirm) showHistoryDialog = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth()
        )

        // Full event history dialog
        if (showHistoryDialog) {
            BackHandler(enabled = true) { showHistoryDialog = false }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showHistoryDialog = false }
            )
            EventHistoryDialog(
                events = uiState.eventLog,
                onDismiss = { showHistoryDialog = false },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Settings overlay
        if (showSettingsDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showSettingsDialog = false }
            )
            com.atlyn.subterranea.ui.settings.SettingsMenu(
                onDismiss = { showSettingsDialog = false },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Exploration event popup with backdrop
        if (showExplorationModal) {
            // Dark backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { viewModel.dismissEvent() }
            )
            
            ExplorationEventCard(
                event = uiState.lastExplorationEvent!!,
                onDismiss = { viewModel.dismissEvent() },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Consolation choice popup (non-producing roll)
        if (showConsolationModal) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
            
            ConsolationChoiceCard(
                onChoice = { choice -> viewModel.handleConsolationChoice(choice) },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // End turn confirmation dialog
        if (showEndTurnDialog) {
            val actionsLeft = uiState.maxActionsPerTurn - uiState.actionsThisTurn
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showEndTurnConfirm = false }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 360.dp)
                        .border(1.dp, Color(0x6600BCD4), RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE111827))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("End turn now?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "You still have $actionsLeft action${if (actionsLeft > 1) "s" else ""} left.",
                            color = Color(0xFFCFD8DC),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Explore, build, or trade first if you want to use them.",
                            color = Color(0xFF90A4AE),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showEndTurnConfirm = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("Keep Playing", color = Color(0xFF00BCD4)) }
                            Button(
                                onClick = { showEndTurnConfirm = false; viewModel.endTurn() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                            ) { Text("End Turn", color = Color.Black, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        
        // Victory screen
        if (uiState.gameOver && uiState.winner != null) {
            VictoryScreen(
                winner = uiState.winner!!,
                metaProgression = metaProg,
                onReplaySeed = { viewModel.replayLastSeed() },
                onNextDifficulty = { viewModel.replayNextDifficulty() },
                onPickNewMap = { viewModel.resetGame() },
                isAtMaxDifficulty = uiState.difficulty == Difficulty.NIGHTMARE,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Defeat screen (turn limit reached)
        if (uiState.gameOver && uiState.winner == null) {
            DefeatScreen(
                finalVP = uiState.totalVPFor(uiState.currentPlayer),
                vpTarget = uiState.victoryPointsToWin,
                turnsPlayed = uiState.turnNumber,
                metaProgression = metaProg,
                onReplaySeed = { viewModel.replayLastSeed() },
                onPickNewMap = { viewModel.resetGame() },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        } // end if (!showDifficultyMenu)
        
        // Difficulty selection screen (shows at game start)
        else {
            val hasSaved by viewModel.hasSavedGame.collectAsState()
            DifficultySelectionScreen(
                onSelectDifficulty = { difficulty -> viewModel.startGameWithDifficulty(difficulty) },
                metaProgression = metaProg,
                hasSavedGame = hasSaved,
                onResumeGame = { viewModel.resumeSavedGame() },
                onDiscardSavedGame = { viewModel.discardSavedGame() },
                unlockedCharacters = viewModel.getUnlockedCharacters(),
                selectedCharacter = viewModel.selectedCharacter.collectAsState().value,
                onSelectCharacter = { c -> viewModel.selectCharacter(c) },
                selectedMapPreset = viewModel.selectedMapPreset.collectAsState().value,
                onSelectMapPreset = { m -> viewModel.selectMapPreset(m) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Phase O-1: first-run coachmark tutorial. Only shows when the game
        // is actually running (difficulty menu hidden), the game isn't over,
        // no other modal is up, and the player has never completed/skipped
        // the tutorial. Sits above the board but below other modals.
        if (!showDifficultyMenu &&
            !uiState.gameOver &&
            !hasHigherPriorityModal &&
            !showEndTurnConfirm &&
            !showHistoryDialog &&
            !metaProg.tutorialSeen
        ) {
            com.atlyn.subterranea.ui.onboarding.TutorialOverlay(
                onComplete = { viewModel.markTutorialSeen() }
            )
        }
    }
}

@Composable
fun TopHUD(uiState: GameState, onSettings: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xDD0D0D1A),
                        Color(0xBB1A1A2E)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x0000BCD4),
                        Color(0x4400BCD4),
                        Color(0x0000BCD4)
                    )
                ),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Turn ${uiState.turnNumber}",
                color = Color(0xFF80DEEA),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = uiState.turnPhase.name.replace("_", " "),
                color = Color(0xFFFFD700),
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Image(
                painter = painterResource(R.drawable.ic_vp_badge),
                contentDescription = "Victory Points",
                modifier = Modifier.size(18.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "${uiState.totalVPFor(uiState.currentPlayer)}/${uiState.victoryPointsToWin}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
            // Phase O-3: settings gear. Tap opens the SettingsMenu modal,
            // which currently exposes "Send feedback" + version info and will
            // host audio toggles in O-2.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSettings)
                    .semantics { contentDescription = "Open settings" },
                contentAlignment = Alignment.Center
            ) {
                Text("⚙", color = Color(0xFFB0BEC5), fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun TutorialHint(hint: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE00ACC1))
    ) {
        Text(
            text = cleanUiText(hint),
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun ResourceBar(player: Player, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xBB1A1A2E),
                        Color(0xDD0D0D1A)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x4400BCD4),
                        Color(0x22007799)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Resource.entries.forEach { resource ->
            ResourceChip(
                resource = resource,
                count = player.getResourceCount(resource),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ResourceChip(resource: Resource, count: Int, modifier: Modifier = Modifier) {
    val color = when (resource) {
        Resource.MYCELIUM -> Color(0xFF9C27B0)
        Resource.BASALT -> Color(0xFF795548)
        Resource.CHITIN -> Color(0xFF4CAF50)
        Resource.LICHEN -> Color(0xFF8BC34A)
        Resource.IRON_ORE -> Color(0xFF607D8B)
        Resource.CRYSTAL -> Color(0xFF00BCD4)
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC101828))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 1.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = IconHelper.resourcePainter(resource),
                contentDescription = resource.displayName(),
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(1.dp))
            Text(
                text = count.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ProductionBadge(
    production: Map<Resource, Int>, 
    isAnimating: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Scale and glow animation when resources are produced
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "productionScale"
    )
    
    // Pulsing glow effect
    val pulseAlpha = if (isAnimating) pulseAlpha(0.7f, 1f, 400) else 1f
    
    // Show a flashy badge of what was just produced
    Card(
        modifier = modifier
            .scale(scale)
            .graphicsLayer { alpha = pulseAlpha },
        colors = CardDefaults.cardColors(
            containerColor = if (isAnimating) Color(0xFF66BB6A) else Color(0xFF4CAF50)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PROD",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold
            )
            production.forEach { (resource, amount) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "+$amount",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isAnimating) 14.sp else 12.sp
                    )
                    Image(
                        painter = IconHelper.resourcePainter(resource),
                        contentDescription = resource.displayName(),
                        modifier = Modifier.size(if (isAnimating) 18.dp else 16.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
fun DiceDisplay(
    diceResult: DiceResult, 
    isAnimating: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Color code the total based on probability (6,7,8 = common, 2,12 = rare)
    val totalColor = when (diceResult.total) {
        6, 7, 8 -> Color(0xFF4CAF50) // Green - common
        5, 9 -> Color(0xFFFFEB3B)    // Yellow - good
        4, 10 -> Color(0xFFFF9800)   // Orange - medium
        3, 11 -> Color(0xFFFF5722)   // Red-orange - uncommon
        else -> Color(0xFFF44336)    // Red - rare (2 or 12)
    }
    
    // Scale animation when dice are shown
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "diceScale"
    )
    
    Row(
        modifier = modifier
            .scale(scale)
            .background(Color(0xDD000000), RoundedCornerShape(24.dp)) // Pill shape
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The two dice
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DiceFace(diceResult.die1, isAnimating)
            DiceFace(diceResult.die2, isAnimating)
        }
        
        // Total and dots merged
        Column(horizontalAlignment = Alignment.Start) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "= ${diceResult.total}",
                    color = totalColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(Modifier.width(6.dp))
                // Probability dots
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    repeat(diceResult.probabilityDots) {
                        Text("•", color = totalColor, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DiceFace(value: Int, isAnimating: Boolean = false) {
    // Rotation animation for dice face
    val rotation by animateFloatAsState(
        targetValue = if (isAnimating) 360f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "diceRotation"
    )
    
    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer { rotationZ = rotation }
            .background(Color.White, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}

@Composable
fun ActionButtons(
    uiState: GameState,
    selectedTile: HexCoordinate?,
    explorableTiles: Set<HexCoordinate>,
    onRollDice: () -> Unit,
    onEndTurn: () -> Unit,
    onBuildClick: () -> Unit,
    onExploreClick: () -> Unit,
    onClearRubble: () -> Unit,
    onTradeClick: () -> Unit,
    onAbilityClick: () -> Unit,
    canTrade: Boolean,
    hasAvailableStructures: Boolean,
    usableAbilities: List<Structure>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Actions remaining indicator
        if (uiState.turnPhase == TurnPhase.MAIN_ACTION) {
            Text(
                text = "Actions: ${uiState.maxActionsPerTurn - uiState.actionsThisTurn} remaining",
                color = if (uiState.actionsThisTurn >= uiState.maxActionsPerTurn) Color(0xFF607D8B) else Color(0xFF80DEEA),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xCC1A1A2E),
                            Color(0xEE0D0D1A)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x4400BCD4),
                            Color(0x22005566)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Roll Dice button - pulses when it's time to roll
            val shouldRoll = uiState.turnPhase == TurnPhase.ROLL_DICE && !uiState.gameOver
            ActionButton(
                text = "Roll",
                enabled = shouldRoll,
                onClick = onRollDice,
                pulse = shouldRoll,
                contentDescription = "Roll dice to start your turn"
            )
            
            // Explore button (New)
            val canExplore = selectedTile != null && selectedTile in explorableTiles && !uiState.gameOver
            if (canExplore) {
                ActionButton(
                    text = "Explore",
                    enabled = true,
                    onClick = onExploreClick,
                    pulse = true,
                    color = Color(0xFF4CAF50),
                    contentDescription = "Explore selected hex tile"
                )
            }

            // Build button — Phase Q-1.2: disabled when nothing is affordable
            // on the selected tile (eliminates the "open empty picker" anti-pattern).
            val canBuild = canBuildAction(
                turnPhase = uiState.turnPhase,
                gameOver = uiState.gameOver,
                selectedTile = selectedTile,
                actionsThisTurn = uiState.actionsThisTurn,
                maxActionsPerTurn = uiState.maxActionsPerTurn,
                hasAvailableStructures = hasAvailableStructures
            )
            val tileIsBuildContext = isBuildContext(
                turnPhase = uiState.turnPhase,
                gameOver = uiState.gameOver,
                selectedTile = selectedTile,
                actionsThisTurn = uiState.actionsThisTurn,
                maxActionsPerTurn = uiState.maxActionsPerTurn
            )

            val buildColor = when {
                canBuild -> Color(0xFF00ACC1)            // primary cyan — go
                tileIsBuildContext -> Color(0xFF607D8B)  // muted blue-grey — selected but unaffordable
                else -> Color(0xFFB0BEC5)                // standard disabled
            }
            val buildContentDescription = when {
                canBuild -> "Build a structure on the selected tile"
                tileIsBuildContext -> "Build disabled — no structures affordable on selected tile"
                else -> "Build a structure on the selected tile, disabled"
            }

            ActionButton(
                text = "Build",
                enabled = canBuild,
                onClick = onBuildClick,
                color = buildColor,
                contentDescription = buildContentDescription
            )
            
            // Clear rubble button
            val selectedTileData = selectedTile?.let { uiState.board[it] }
            val canClear = selectedTileData?.hasRubble == true &&
                uiState.currentPlayer.canAfford(mapOf(Resource.IRON_ORE to 1, Resource.BASALT to 1))
            ActionButton(
                text = "Clear",
                enabled = canClear,
                onClick = onClearRubble,
                contentDescription = "Clear rubble from selected tile, costs one iron ore and one basalt"
            )
            
            // Trade button (4:1 resource exchange)
            ActionButton(
                text = "Trade",
                enabled = canTrade && uiState.turnPhase == TurnPhase.MAIN_ACTION &&
                    uiState.actionsThisTurn < uiState.maxActionsPerTurn,
                onClick = onTradeClick,
                color = Color(0xFF9C27B0),
                contentDescription = "Open trade menu to exchange resources"
            )

            // Ability button (only shown when at least one ability is usable)
            if (usableAbilities.isNotEmpty() && uiState.turnPhase == TurnPhase.MAIN_ACTION) {
                ActionButton(
                    text = if (usableAbilities.size > 1) "Ability·${usableAbilities.size}" else "Ability",
                    enabled = uiState.actionsThisTurn < uiState.maxActionsPerTurn,
                    onClick = onAbilityClick,
                    color = Color(0xFFFFC107),
                    contentDescription = "Use a structure ability, ${usableAbilities.size} available"
                )
            }

            // End Turn button
            ActionButton(
                text = "End",
                enabled = uiState.turnPhase != TurnPhase.ROLL_DICE,
                onClick = onEndTurn,
                color = Color(0xFFFF9800),
                contentDescription = "End your turn"
            )
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    color: Color = Color(0xFF00ACC1),
    pulse: Boolean = false,
    contentDescription: String? = null
) {
    // Press animation state
    var isPressed by remember { mutableStateOf(false) }
    
    // Scale animation for press effect
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.92f
            pulse && enabled -> pulseScale(1f, 1.05f, 800)
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "buttonScale"
    )
    
    val description = contentDescription ?: text
    val stateSuffix = if (enabled) "" else ", disabled"

    Box(
        modifier = Modifier
            .height(42.dp)
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(
                brush = Brush.verticalGradient(
                    colors = if (enabled) {
                        listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.5f))
                    } else {
                        listOf(Color(0xFF485266), Color(0xFF343B4A))
                    }
                ),
                shape = RoundedCornerShape(50)
            )
            .border(
                width = 1.dp,
                color = if (enabled) color.copy(alpha = 0.8f) else Color(0xFF5F687A),
                shape = RoundedCornerShape(50)
            )
            .then(
                if (enabled && pulse) {
                    Modifier.border(
                        width = 2.dp,
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0.2f))
                        ),
                        shape = RoundedCornerShape(50)
                    )
                } else Modifier
            )
            .clickable(enabled = enabled) {
                isPressed = true
                onClick()
            }
            .padding(horizontal = 10.dp)
            .semantics {
                this.contentDescription = description + stateSuffix
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 11.sp,
            color = if (enabled) Color.White else Color(0xFFCFD8DC),
            fontWeight = FontWeight.SemiBold
        )
    }
    
    // Reset press state after animation
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(150)
            isPressed = false
        }
    }
}

@Composable
fun BuildMenu(
    availableStructures: List<Pair<StructureType, Map<Resource, Int>>>,
    player: Player,
    selectedTileName: String? = null,
    onBuild: (StructureType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(340.dp)
            .heightIn(max = 420.dp)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x6600BCD4),
                        Color(0x33005566)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xF01A1A2E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Build Structure",
                    color = Color(0xFF80DEEA),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                TextButton(onClick = onDismiss) {
                    Text("✕", color = Color(0xFF607D8B))
                }
            }
            
            Divider(
                color = Color(0x4400BCD4),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            if (selectedTileName != null) {
                Text(
                    "Building on: $selectedTileName",
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                Text(
                    "Select a tile first",
                    color = Color(0xFFFF9800),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Show all structure types, highlighting which are available.
            // The cost shown is the *actual* cost (post Phase O-4 Lantern
            // scaling and difficulty/character adjustment) when the structure
            // is on the available list; otherwise we fall back to its base
            // cost so the player still sees a recognisable price tag.
            val allTypes = StructureType.entries.toList()
            val availableMap = availableStructures.toMap()
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(allTypes) { type ->
                    val isAvailable = type in availableMap
                    val displayCost = availableMap[type] ?: type.cost
                    val canAfford = player.canAfford(displayCost)
                    StructureCard(
                        structureType = type,
                        cost = displayCost,
                        player = player,
                        canAfford = canAfford,
                        isAvailable = isAvailable,
                        onClick = { if (isAvailable) onBuild(type) }
                    )
                }
            }
        }
    }
}

@Composable
fun AbilityMenu(
    usableAbilities: List<Structure>,
    onUseAbility: (Structure) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(340.dp)
            .heightIn(max = 420.dp)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x66FFC107),
                        Color(0x33996600)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .semantics {
                contentDescription = "Use a structure ability. ${usableAbilities.size} available."
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xF01A1A2E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Use Ability",
                    color = Color(0xFFFFC107),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { contentDescription = "Close ability menu" }
                ) {
                    Text("✕", color = Color(0xFF607D8B))
                }
            }

            Divider(
                color = Color(0x44FFC107),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (usableAbilities.isEmpty()) {
                Text(
                    "No abilities available right now. Build structures with active abilities (Lantern, Outpost, Excavator, Fungal Farm) and wait for cooldowns to expire.",
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp
                )
            } else {
                Text(
                    "Tap an ability to use it. Uses 1 action.",
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(usableAbilities) { structure ->
                        val ability = structure.type.ability
                        if (ability != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUseAbility(structure) }
                                    .semantics {
                                        contentDescription = "Use ${ability.displayName} from ${structure.type.displayName}. ${ability.description}"
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            ability.displayName,
                                            color = Color(0xFFFFC107),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "from ${structure.type.displayName}",
                                            color = Color(0xFF80DEEA),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        ability.description,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StructureCard(
    structureType: StructureType,
    cost: Map<Resource, Int>,
    player: Player,
    canAfford: Boolean,
    isAvailable: Boolean,
    onClick: () -> Unit
) {
    val enabled = canAfford && isAvailable
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (!enabled) Modifier.alpha(0.6f) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color(0xFF2D2D44) else Color(0xFF1A1A1A)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    structureType.displayName,
                    color = if (enabled) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${structureType.victoryPoints} VP",
                    color = Color(0xFFFFD700)
                )
            }
            
            Text(
                structureType.description,
                color = Color.Gray,
                fontSize = 12.sp
            )
            
            Spacer(Modifier.height(4.dp))
            
            // Cost display with affordability per resource
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cost.forEach { (resource, amount) ->
                    val have = player.getResourceCount(resource)
                    val enough = have >= amount
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = IconHelper.resourcePainter(resource),
                            contentDescription = resource.displayName(),
                            modifier = Modifier.size(14.dp),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            amount.toString(),
                            fontSize = 12.sp,
                            color = if (enough) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Show what's missing if can't afford
            if (!canAfford) {
                val missing = cost.mapNotNull { (resource, needed) ->
                    val have = player.getResourceCount(resource)
                    if (have < needed) "Need ${needed - have} more ${resource.displayName()}" else null
                }
                if (missing.isNotEmpty()) {
                    Text(
                        missing.joinToString(", "),
                        color = Color(0xFFFF6B6B),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            // Show if not available on this tile
            if (canAfford && !isAvailable) {
                Text(
                    if (structureType == StructureType.EXCAVATOR) "Requires Outpost on tile" else "Cannot build here",
                    color = Color(0xFFFF9800),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun TradeMenu(
    tradableResources: List<Resource>,
    player: Player,
    tradeRatio: Int = 4,
    onTrade: (give: Resource, receive: Resource) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(340.dp)
            .heightIn(max = 500.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE1A1A2E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Trade Resources",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                TextButton(onClick = onDismiss) {
                    Text("✕", color = Color.White)
                }
            }
            
            Text(
                "Trade $tradeRatio of one resource for 1 of another",
                color = Color.Gray,
                fontSize = 12.sp
            )
            
            Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            
            if (tradableResources.isEmpty()) {
                Text(
                    "Need $tradeRatio+ of any resource to trade.\nGather more resources first!",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            } else {
                Text(
                    "Select resource to give ($tradeRatio):",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                
                Spacer(Modifier.height(8.dp))
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tradableResources) { giveResource ->
                        TradeOptionCard(
                            giveResource = giveResource,
                            playerAmount = player.getResourceCount(giveResource),
                            tradeRatio = tradeRatio,
                            allResources = Resource.entries.filter { it != giveResource },
                            onTrade = { receive -> onTrade(giveResource, receive) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TradeOptionCard(
    giveResource: Resource,
    playerAmount: Int,
    tradeRatio: Int = 4,
    allResources: List<Resource>,
    onTrade: (receive: Resource) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = IconHelper.resourcePainter(giveResource),
                    contentDescription = giveResource.displayName(),
                    modifier = Modifier.size(16.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    "Give $tradeRatio ${giveResource.displayName()} (have $playerAmount)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(6.dp))

            // Phase O-1: replaced the row-locked LazyRow with a non-scrolling
            // Row of weighted buttons. K-2 reported that the inner LazyRow
            // could only be scrolled by swiping at its exact Y, and that
            // Luminary Crystal was hidden off-screen on the right. With weight=1
            // every receive option fits inside the card and is always visible.
            // Crystal is reordered to the front so the most strategic target
            // is the easiest to spot.
            val orderedReceiveOptions = allResources.sortedByDescending { it == Resource.CRYSTAL }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                orderedReceiveOptions.forEach { receiveResource ->
                    Button(
                        onClick = { onTrade(receiveResource) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics {
                                contentDescription =
                                    "Trade $tradeRatio ${giveResource.displayName()} for 1 ${receiveResource.displayName()}"
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = IconHelper.resourcePainter(receiveResource),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                receiveResource.displayName().take(3),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventTicker(events: List<String>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Single-line ticker showing only the most recent event. Tap to open full history.
    // Hidden entirely when there are no events so it doesn't reserve space.
    val latest = events.firstOrNull() ?: return
    val cleaned = cleanUiText(latest)
    val tickerDescription = "Latest event: $cleaned. Tap to view full history."
    Row(
        modifier = modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC111827))
            .border(1.dp, Color(0x5529B6F6), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = tickerDescription }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            cleaned,
            color = Color.LightGray.copy(alpha = 0.95f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "▴",
            color = Color(0xFF29B6F6),
            fontSize = 12.sp
        )
    }
}

@Composable
fun EventHistoryDialog(
    events: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 420.dp)
            .heightIn(max = 480.dp)
            .border(1.dp, Color(0x6629B6F6), RoundedCornerShape(18.dp))
            // Consume taps so background clicks (which dismiss) don't fire
            // on padding/empty regions of the card itself.
            .clickable(enabled = true, onClick = {}),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE111827))
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Game log",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${events.size} event${if (events.size != 1) "s" else ""}",
                    color = Color(0xFF90A4AE),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            if (events.isEmpty()) {
                Text(
                    "No events yet.",
                    color = Color(0xFF90A4AE),
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(events) { event ->
                        Text(
                            cleanUiText(event),
                            color = Color.LightGray.copy(alpha = 0.95f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x33000000), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .semantics { contentDescription = cleanUiText(event) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6)),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ExplorationEventCard(
    event: ExplorationEvent,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when (event) {
        is ExplorationEvent.TreasureCache -> Color(0xFF4CAF50)
        is ExplorationEvent.CrystalVein -> Color(0xFF00BCD4)
        is ExplorationEvent.AncientArtifact -> Color(0xFFFFD700)
        is ExplorationEvent.BeetleNest -> Color(0xFF8BC34A)
        is ExplorationEvent.FungalBloom -> Color(0xFF9C27B0)
        is ExplorationEvent.CaveIn -> Color(0xFFF44336)
        is ExplorationEvent.GasLeak -> Color(0xFF9E9E9E)
        is ExplorationEvent.MagmaBurst -> Color(0xFFFF5722)
        is ExplorationEvent.Tremor -> Color(0xFF795548)
        is ExplorationEvent.GeothermalVent -> Color(0xFFFF9800)
        is ExplorationEvent.LostMiner -> Color(0xFF2196F3)
        ExplorationEvent.StableGround -> Color.Gray
    }
    
    Card(
        modifier = modifier
            .width(280.dp)
            .clickable { onDismiss() },
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color.White.copy(alpha = 0.8f), CircleShape)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                event.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                event.description,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.25f)
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    "OK",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun ConsolationChoiceCard(
    onChoice: (RollConsolation) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(300.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE1A1A2E))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No Production!",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose your consolation:",
                color = Color.Gray,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            
            RollConsolation.entries.forEach { choice ->
                Button(
                    onClick = { onChoice(choice) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (choice) {
                            RollConsolation.GAIN_RESOURCE -> Color(0xFF4CAF50)
                            RollConsolation.BONUS_ACTION -> Color(0xFFFF9800)
                            RollConsolation.DISCOUNT_TRADE -> Color(0xFF2196F3)
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        choice.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        choice.description,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
fun VictoryScreen(
    winner: Player,
    metaProgression: MetaProgression,
    onReplaySeed: () -> Unit,
    onNextDifficulty: () -> Unit,
    onPickNewMap: () -> Unit,
    isAtMaxDifficulty: Boolean,
    modifier: Modifier = Modifier
) {
    // Celebration animations
    val infiniteTransition = rememberInfiniteTransition(label = "victory")
    
    // Pulsing glow for the card
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    // Bouncing emoji
    val emojiScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emojiScale"
    )
    
    // Rotating trophy effect
    val rotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )
    
    // Scale in animation for the card
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    val cardScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )
    
    Box(
        modifier = modifier.background(Color(0xEE000000)),
        contentAlignment = Alignment.Center
    ) {
        // Glow effect behind card
        Box(
            modifier = Modifier
                .size(350.dp)
                .background(
                    Color(0xFFFFD700).copy(alpha = glowAlpha * 0.3f),
                    RoundedCornerShape(24.dp)
                )
        )
        
        Card(
            modifier = Modifier.scale(cardScale),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "VICTORY",
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Black,
                    fontSize = 34.sp,
                    modifier = Modifier
                        .scale(emojiScale)
                        .graphicsLayer { rotationZ = rotation }
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "VICTORY!",
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${winner.name} has won!",
                    color = Color.White,
                    fontSize = 18.sp
                )
                Text(
                    "Final Score: ${winner.calculateVictoryPoints() + winner.victoryPoints} VP", // Winner VP already includes lantern bonus via checkVictory
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                
                Spacer(Modifier.height(24.dp))
                
                // Show achievements with staggered animation
                if (winner.achievements.isNotEmpty()) {
                    Text("Achievements:", color = Color.White, fontWeight = FontWeight.Bold)
                    winner.achievements.forEachIndexed { index, achievement ->
                        var achievementVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(300L + index * 200L)
                            achievementVisible = true
                        }
                        
                        val achievementScale by animateFloatAsState(
                            targetValue = if (achievementVisible) 1f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "achievementScale"
                        )
                        
                        Text(
                            achievement.displayName,
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            modifier = Modifier.scale(achievementScale)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                
                // Lifetime stats
                Divider(color = Color.Gray.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Lifetime: ${metaProgression.gamesWon}W/${metaProgression.gamesPlayed}G • ${metaProgression.totalVPEarned} VP",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))
                
                Button(
                    onClick = onReplaySeed,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(pulseScale(1f, 1.03f, 1000))
                        .semantics { contentDescription = "Replay this map with the same seed" }
                ) {
                    Text("▶  Replay this seed", color = Color.White, fontWeight = FontWeight.Bold)
                }
                if (!isAtMaxDifficulty) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onNextDifficulty,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Try the next difficulty" }
                    ) {
                        Text("⬆  Try next difficulty", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onPickNewMap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Pick a new character or map" }
                ) {
                    Text("Pick a new map", color = Color(0xFFB0BEC5))
                }
            }
        }
    }
}

@Composable
fun DefeatScreen(
    finalVP: Int,
    vpTarget: Int,
    turnsPlayed: Int,
    metaProgression: MetaProgression,
    onReplaySeed: () -> Unit,
    onPickNewMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val cardScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )

    Box(
        modifier = modifier
            .background(Color(0xEE000000))
            .clickable { /* consume touch to prevent interaction with game behind */ },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.scale(cardScale).widthIn(max = 340.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "TIME'S UP",
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "The caves have collapsed...",
                    color = Color(0xFFB0BEC5),
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "$finalVP / $vpTarget VP",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Text(
                    "Reached turn $turnsPlayed",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))

                val vpShort = vpTarget - finalVP
                if (vpShort > 0) {
                    Text(
                        "Just $vpShort VP short!",
                        color = Color(0xFFFF9800),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(16.dp))
                Divider(color = Color.Gray.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Lifetime: ${metaProgression.gamesWon}W/${metaProgression.gamesPlayed}G",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onReplaySeed,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Replay this map with the same seed" }
                ) {
                    Text("▶  Replay this seed", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onPickNewMap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Pick a new character, difficulty, or map" }
                ) {
                    Text("Pick a new map", color = Color(0xFFB0BEC5))
                }
            }
        }
    }
}

@Composable
fun SelectedTileInfo(
    tile: HexTile,
    structure: Structure?,
    canExplore: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(170.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD1A1A2E))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Zone indicator
            val zoneColor = when (tile.zone) {
                Zone.SURFACE -> Color(0xFF4CAF50)
                Zone.CRUST -> Color(0xFF8BC34A)
                Zone.MANTLE -> Color(0xFFFF9800)
                Zone.CORE -> Color(0xFFF44336)
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(zoneColor, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    tile.zone.name,
                    color = zoneColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            if (!tile.isRevealed) {
                val (riskIconRes, riskLabel, riskColor) = when (tile.zone) {
                    Zone.SURFACE -> Triple(R.drawable.ic_badge_safe, "Safe", Color(0xFF4CAF50))
                    Zone.CRUST -> Triple(R.drawable.ic_badge_moderate, "Moderate risk", Color(0xFFFFEB3B))
                    Zone.MANTLE -> Triple(R.drawable.ic_badge_risky, "Risky — better rewards", Color(0xFFFF9800))
                    Zone.CORE -> Triple(R.drawable.ic_badge_dangerous, "Dangerous — richest rewards", Color(0xFFF44336))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(riskIconRes),
                        contentDescription = riskLabel,
                        modifier = Modifier.size(18.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Unexplored — $riskLabel",
                        color = riskColor,
                        fontSize = 14.sp
                    )
                }
                if (canExplore) {
                    Text(
                        "Tap to explore!",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp
                    )
                }
            } else {
                // Terrain type
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = IconHelper.terrainPainter(tile.terrain),
                        contentDescription = tile.terrain.displayName(),
                        modifier = Modifier.size(16.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        tile.terrain.displayName(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                
                // Production number
                tile.numberToken?.let { number ->
                    val secondary = tile.secondaryNumberToken
                    val numberText = if (secondary != null) "Produces on: $number or $secondary" else "Produces on: $number"
                    Text(
                        numberText,
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
                
                // Status indicators
                if (tile.hasRubble) {
                    Text(
                        "Rubble present",
                        color = Color(0xFFF44336),
                        fontSize = 12.sp
                    )
                }
                
                if (!tile.isIlluminated) {
                    Text(
                        "Not illuminated",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Text(
                        "Build a Lantern nearby!",
                        color = Color(0xFFFFEB3B),
                        fontSize = 10.sp
                    )
                }
                
                // Structure info
                structure?.let {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = IconHelper.structurePainter(it.type),
                            contentDescription = it.type.displayName,
                            modifier = Modifier.size(14.dp),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            it.type.displayName,
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper function to determine specific reason for build failure
 */
private fun getBuildFailureReason(player: Player, uiState: GameState?): String {
    // Check if player has ANY resources to build anything
    val cheapestCost = StructureType.entries.minOfOrNull { type ->
        type.cost.values.sum()
    } ?: 4
    
    val totalResources = player.resources.values.sum()
    if (totalResources < cheapestCost) {
        return "Not enough resources.\nGather more by rolling dice!"
    }
    
    // Check each structure type and find which resources are missing
    val missingResources = mutableListOf<String>()
    for (type in StructureType.entries) {
        val missing = type.cost.filter { (resource, needed) ->
            player.getResourceCount(resource) < needed
        }
        if (missing.isEmpty()) {
            // Player can afford at least one structure
            return "This tile already has a structure\nor is not suitable for building."
        }
        missingResources.addAll(missing.map { (res, need) -> 
            "${res.displayName()}: need $need, have ${player.getResourceCount(res)}"
        })
    }
    
    // Show most common missing resource
    val resourceCounts = missingResources.groupingBy { it.substringBefore(":") }.eachCount()
    val mostNeeded = resourceCounts.maxByOrNull { it.value }?.key ?: "resources"
    val tradeRatio = uiState?.difficulty?.tradeRatio ?: 4
    
    return "Insufficient resources.\nMost needed: $mostNeeded\n\nTip: Try trading at $tradeRatio:1 ratio."
}

@Composable
fun DifficultySelectionScreen(
    onSelectDifficulty: (Difficulty) -> Unit,
    metaProgression: MetaProgression = MetaProgression(),
    hasSavedGame: Boolean = false,
    onResumeGame: () -> Unit = {},
    onDiscardSavedGame: () -> Unit = {},
    unlockedCharacters: List<GameCharacter> = listOf(GameCharacter.EXPLORER),
    selectedCharacter: GameCharacter = GameCharacter.EXPLORER,
    onSelectCharacter: (GameCharacter) -> Unit = {},
    selectedMapPreset: MapPreset = MapPreset.STANDARD,
    onSelectMapPreset: (MapPreset) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Animation for screen appearance
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    val cardScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .background(Color(0xEE000000))
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .scale(cardScale)
                .padding(16.dp)
                .widthIn(max = 380.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title with mining theme
                Text(
                    "SubTerrania",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700)
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Resume previous game (if a save exists)
                if (hasSavedGame) {
                    Button(
                        onClick = onResumeGame,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Resume your previous game in progress" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32)
                        )
                    ) {
                        Text(
                            "▶  Resume Game",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onDiscardSavedGame,
                        modifier = Modifier.semantics {
                            contentDescription = "Discard the saved game and start a new one"
                        }
                    ) {
                        Text(
                            "Discard saved game",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Divider(color = Color.Gray.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    "Select Difficulty",
                    fontSize = 18.sp,
                    color = Color.White
                )
                
                Spacer(Modifier.height(24.dp))
                
                // Difficulty buttons
                Difficulty.entries.forEachIndexed { index, difficulty ->
                    var buttonVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(100L + index * 100L)
                        buttonVisible = true
                    }
                    
                    val buttonScale by animateFloatAsState(
                        targetValue = if (buttonVisible) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "buttonScale$index"
                    )
                    
                    DifficultyButton(
                        difficulty = difficulty,
                        onClick = { onSelectDifficulty(difficulty) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(buttonScale)
                    )
                    
                    if (index < Difficulty.entries.lastIndex) {
                        Spacer(Modifier.height(12.dp))
                    }
                }
                
                Spacer(Modifier.height(20.dp))

                // Phase O-1: Character picker. Shows unlocked characters as
                // tappable chips with the selected character's bonus blurb
                // displayed below — addresses K-2's "mystery pre-built Lantern"
                // confusion (players never knew the EXPLORER was even a choice).
                CharacterPickerSection(
                    characters = unlockedCharacters,
                    selected = selectedCharacter,
                    onSelect = onSelectCharacter
                )

                Spacer(Modifier.height(16.dp))

                // Phase O-1: Map preset picker.
                MapPresetPickerSection(
                    selected = selectedMapPreset,
                    onSelect = onSelectMapPreset
                )

                Spacer(Modifier.height(20.dp))

                // Legend
                Text(
                    "Higher difficulty = more hazards,\nfewer resources, harder trades",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                
                // Show lifetime stats if any games played
                if (metaProgression.gamesPlayed > 0) {
                    Spacer(Modifier.height(12.dp))
                    Divider(color = Color.Gray.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${metaProgression.gamesWon}/${metaProgression.gamesPlayed} wins • ${metaProgression.totalVPEarned} total VP • ${metaProgression.lifetimeAchievements.size} achievements",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultyButton(
    difficulty: Difficulty,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (color, description) = when (difficulty) {
        Difficulty.EASY -> Color(0xFF4CAF50) to "Relaxed mining • More resources • Forgiving"
        Difficulty.NORMAL -> Color(0xFF2196F3) to "Balanced experience • Standard rules"
        Difficulty.HARD -> Color(0xFFFF9800) to "Challenging • Scarce resources • More hazards"
        Difficulty.NIGHTMARE -> Color(0xFFF44336) to "Brutal • Extreme scarcity • Constant danger"
    }
    
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(color, CircleShape)
            )
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    difficulty.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = color
                )
                Text(
                    description,
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
                
                // Show key stats
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip("${difficulty.victoryPointsToWin} VP", Color.White)
                    StatChip("${difficulty.maxActionsPerTurn}/turn", Color.White)
                    StatChip("${difficulty.tradeRatio}:1", Color.White)
                }
            }
        }
    }
}

@Composable
private fun StatChip(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = color.copy(alpha = 0.8f),
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

/**
 * Phase O-1: horizontal row of unlocked-character chips. Tapping a chip selects
 * that character; the selected character's bonus blurb is shown below the row
 * so the player understands what they're choosing.
 */
@Composable
private fun CharacterPickerSection(
    characters: List<GameCharacter>,
    selected: GameCharacter,
    onSelect: (GameCharacter) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Character",
            fontSize = 14.sp,
            color = Color(0xFFCFD8DC),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            items(characters) { character ->
                val isSelected = character == selected
                val borderColor = if (isSelected) Color(0xFFFFD700) else Color(0x55FFFFFF)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFF263238) else Color(0xFF1A1A2E))
                        .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable { onSelect(character) }
                        .semantics {
                            contentDescription =
                                "${character.displayName}. ${character.description}." +
                                if (isSelected) " Selected." else " Tap to select."
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(character.emoji, fontSize = 26.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${selected.emoji} ${selected.displayName}",
            fontSize = 13.sp,
            color = Color(0xFFFFD700),
            fontWeight = FontWeight.Bold
        )
        Text(
            selected.description,
            fontSize = 11.sp,
            color = Color(0xFFB0BEC5),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/**
 * Phase O-1: horizontal row of map-preset chips. Same pattern as
 * [CharacterPickerSection] but for [MapPreset].
 */
@Composable
private fun MapPresetPickerSection(
    selected: MapPreset,
    onSelect: (MapPreset) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Map",
            fontSize = 14.sp,
            color = Color(0xFFCFD8DC),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            items(MapPreset.entries.toList()) { preset ->
                val isSelected = preset == selected
                val borderColor = if (isSelected) Color(0xFFFFD700) else Color(0x55FFFFFF)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFF263238) else Color(0xFF1A1A2E))
                        .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable { onSelect(preset) }
                        .semantics {
                            contentDescription =
                                "${preset.displayName}. ${preset.description}." +
                                if (isSelected) " Selected." else " Tap to select."
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(preset.emoji, fontSize = 26.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${selected.emoji} ${selected.displayName}",
            fontSize = 13.sp,
            color = Color(0xFFFFD700),
            fontWeight = FontWeight.Bold
        )
        Text(
            selected.description,
            fontSize = 11.sp,
            color = Color(0xFFB0BEC5),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

private fun cleanUiText(value: String): String {
    return value
        .replace(Regex("[\\p{So}\\p{Cs}]"), "")
        .replace(Regex("[\\uFE00-\\uFE0F]"), "")
        .replace(Regex("\\s{2,}"), " ")
        .trim()
}
