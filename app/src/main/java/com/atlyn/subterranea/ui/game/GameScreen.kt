package com.atlyn.subterranea.ui.game

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
import com.atlyn.subterranea.ui.util.IconHelper
import com.atlyn.subterranea.ui.viewmodel.GameViewModel

@Composable
fun GameScreen(
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // End turn confirmation dialog state
    var showEndTurnConfirm by remember { mutableStateOf(false) }
    
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
            selectedTile = uiState.selectedTile,
            explorableTiles = explorableTiles,
            buildableTiles = buildableTiles,
            onTileClick = { coord -> viewModel.onTileClicked(coord) },
            modifier = Modifier.fillMaxSize().padding(top = 280.dp, bottom = 220.dp)
        )
        
        // Top HUD - Turn info and VP
        TopHUD(
            uiState = uiState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        // Tutorial hint
        if (uiState.showTutorial) {
            uiState.getCurrentHint()?.let { hint ->
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
                .padding(top = 160.dp, start = 8.dp, end = 8.dp)
        )
        
        // Dice display with production info
        if (uiState.lastDiceResult != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 220.dp)
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
        if (uiState.selectedTile != null && !uiState.showBuildMenu) {
            val tile = uiState.board[uiState.selectedTile]
            if (tile != null) {
                SelectedTileInfo(
                    tile = tile,
                    structure = uiState.getStructureAt(uiState.selectedTile!!),
                    canExplore = !tile.isRevealed && uiState.canExploreThisTurn && 
                        uiState.turnPhase == TurnPhase.MAIN_ACTION,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 100.dp)
                )
            }
        }
        
        // Action buttons
        val availableStructures = remember(uiState.selectedTile, uiState.currentPlayer.resources) {
            if (uiState.selectedTile != null) viewModel.getAvailableStructures() else emptyList()
        }

        ActionButtons(
            uiState = uiState,
            explorableTiles = explorableTiles,
            onRollDice = { viewModel.rollDice() },
            onEndTurn = {
                val actionsLeft = uiState.maxActionsPerTurn - uiState.actionsThisTurn
                if (actionsLeft > 0 && uiState.turnPhase == TurnPhase.MAIN_ACTION) {
                    showEndTurnConfirm = true
                } else {
                    viewModel.endTurn()
                }
            },
            onBuildClick = { viewModel.toggleBuildMenu() },
            onExploreClick = { viewModel.exploreSelectedTile() },
            onClearRubble = { viewModel.clearRubble() },
            onTradeClick = { viewModel.toggleTradeMenu() },
            canTrade = viewModel.canTrade(),
            hasAvailableStructures = availableStructures.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
        // Trade menu state
        val showTradeMenu by viewModel.showTradeMenu.collectAsState()
        
        // Build menu popup with backdrop
        if (uiState.showBuildMenu) {
            // Dark backdrop that dismisses on tap
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { viewModel.toggleBuildMenu() }
            )
            
            BuildMenu(
                availableStructures = if (uiState.selectedTile != null) viewModel.getAvailableStructures() else emptyList(),
                player = uiState.currentPlayer,
                selectedTileName = uiState.selectedTile?.let { coord ->
                    uiState.board[coord]?.terrain?.displayName()
                },
                onBuild = { type -> viewModel.buildStructure(type) },
                onDismiss = { viewModel.toggleBuildMenu() },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Trade menu popup with backdrop
        if (showTradeMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { viewModel.toggleTradeMenu() }
            )
            
            TradeMenu(
                tradableResources = viewModel.getTradableResources(),
                player = uiState.currentPlayer,
                tradeRatio = if (uiState.discountTradeAvailable) 2 else uiState.difficulty.tradeRatio,
                onTrade = { give, receive -> viewModel.tradeResources(give, receive) },
                onDismiss = { viewModel.toggleTradeMenu() },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Event log (bottom strip) - always visible for context
        EventLog(
            events = uiState.eventLog,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 170.dp, start = 8.dp, end = 8.dp)
                .fillMaxWidth()
        )
        
        // Exploration event popup with backdrop
        if (uiState.lastExplorationEvent != null && 
            uiState.lastExplorationEvent !is ExplorationEvent.StableGround) {
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
        if (uiState.pendingConsolation) {
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
        if (showEndTurnConfirm) {
            val actionsLeft = uiState.maxActionsPerTurn - uiState.actionsThisTurn
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showEndTurnConfirm = false },
                title = { Text("End Turn?", color = Color.White) },
                text = { 
                    Text(
                        "You still have $actionsLeft action${if (actionsLeft > 1) "s" else ""} remaining!\n\nYou can explore tiles, build structures, or trade resources.",
                        color = Color(0xFFB0BEC5)
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showEndTurnConfirm = false; viewModel.endTurn() }
                    ) { Text("End Turn", color = Color(0xFFFF9800)) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showEndTurnConfirm = false }
                    ) { Text("Keep Playing", color = Color(0xFF00BCD4)) }
                },
                containerColor = Color(0xFF1A1A2E),
                titleContentColor = Color.White
            )
        }
        
        // Victory screen
        if (uiState.gameOver && uiState.winner != null) {
            VictoryScreen(
                winner = uiState.winner!!,
                metaProgression = metaProg,
                onRestart = { viewModel.resetGame() },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        } // end if (!showDifficultyMenu)
        
        // Difficulty selection screen (shows at game start)
        if (showDifficultyMenu) {
            DifficultySelectionScreen(
                onSelectDifficulty = { difficulty -> viewModel.startGameWithDifficulty(difficulty) },
                metaProgression = metaProg,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun TopHUD(uiState: GameState, modifier: Modifier = Modifier) {
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
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Turn ${uiState.turnNumber}",
            color = Color(0xFF80DEEA),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = uiState.turnPhase.name.replace("_", " "),
            color = Color(0xFFFFD700),
            fontSize = 14.sp
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "⭐",
                fontSize = 18.sp
            )
            Text(
                text = " ${uiState.totalVPFor(uiState.currentPlayer)}/${uiState.victoryPointsToWin} VP",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TutorialHint(hint: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE00ACC1))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💡",
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = hint,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
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
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
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
    
    Row(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.4f),
                        color.copy(alpha = 0.2f)
                    )
                ),
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = IconHelper.resourcePainter(resource),
            contentDescription = resource.displayName(),
            modifier = Modifier.size(22.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = count.toString(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
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
            Text("⛏️", fontSize = 12.sp)
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
    explorableTiles: Set<HexCoordinate>,
    onRollDice: () -> Unit,
    onEndTurn: () -> Unit,
    onBuildClick: () -> Unit,
    onExploreClick: () -> Unit,
    onClearRubble: () -> Unit,
    onTradeClick: () -> Unit,
    canTrade: Boolean,
    hasAvailableStructures: Boolean,
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
                fontSize = 12.sp,
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
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Roll Dice button - pulses when it's time to roll
            val shouldRoll = uiState.turnPhase == TurnPhase.ROLL_DICE
            ActionButton(
                text = "🎲 Roll",
                enabled = shouldRoll,
                onClick = onRollDice,
                pulse = shouldRoll
            )
            
            // Explore button (New)
            val canExplore = uiState.selectedTile != null && uiState.selectedTile in explorableTiles
            if (canExplore) {
                ActionButton(
                    text = "🔍 Explore",
                    enabled = true,
                    onClick = onExploreClick,
                    pulse = true,
                    color = Color(0xFF4CAF50)
                )
            }

            // Build button
            val canBuild = uiState.turnPhase == TurnPhase.MAIN_ACTION && 
                uiState.selectedTile != null &&
                uiState.actionsThisTurn < uiState.maxActionsPerTurn
            
            val buildColor = if (canBuild && !hasAvailableStructures) Color(0xFFEF5350) else Color(0xFF00ACC1)
            val buildText = if (canBuild && !hasAvailableStructures) "⚠️ Build" else "🏗️ Build"

            ActionButton(
                text = buildText,
                enabled = canBuild,
                onClick = onBuildClick,
                color = buildColor
            )
            
            // Clear rubble button
            val selectedTile = uiState.selectedTile?.let { uiState.board[it] }
            val canClear = selectedTile?.hasRubble == true &&
                uiState.currentPlayer.canAfford(mapOf(Resource.IRON_ORE to 1, Resource.BASALT to 1))
            ActionButton(
                text = "🧹 Clear",
                enabled = canClear,
                onClick = onClearRubble
            )
            
            // Trade button (4:1 resource exchange)
            ActionButton(
                text = "🔄 Trade",
                enabled = canTrade && uiState.turnPhase == TurnPhase.MAIN_ACTION,
                onClick = onTradeClick,
                color = Color(0xFF9C27B0)
            )
            
            // End Turn button
            ActionButton(
                text = "➡️ End",
                enabled = uiState.turnPhase != TurnPhase.ROLL_DICE,
                onClick = onEndTurn,
                color = Color(0xFFFF9800)
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
    pulse: Boolean = false
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
    
    Button(
        onClick = {
            isPressed = true
            onClick()
        },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = Color(0xFF2A2A4A)
        ),
        modifier = Modifier
            .height(44.dp)
            .scale(scale)
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
            ),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(text, fontSize = 13.sp, color = Color.White)
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
    availableStructures: List<StructureType>,
    player: Player,
    selectedTileName: String? = null,
    onBuild: (StructureType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(320.dp)
            .heightIn(max = 400.dp)
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
                    "🏗️ Build Structure",
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
                    "📍 Building on: $selectedTileName",
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                Text(
                    "⚠️ Select a tile first",
                    color = Color(0xFFFF9800),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Show all structure types, highlighting which are available
            val allTypes = StructureType.entries.toList()
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allTypes) { type ->
                    val isAvailable = type in availableStructures
                    val canAfford = player.canAfford(type.cost)
                    StructureCard(
                        structureType = type,
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
fun StructureCard(
    structureType: StructureType,
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
                    "⭐${structureType.victoryPoints}",
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                structureType.cost.forEach { (resource, amount) ->
                    val have = player.getResourceCount(resource)
                    val enough = have >= amount
                    val emoji = when (resource) {
                        Resource.MYCELIUM -> "🍄"
                        Resource.BASALT -> "🧱"
                        Resource.CHITIN -> "🪲"
                        Resource.LICHEN -> "🌿"
                        Resource.IRON_ORE -> "⚙️"
                        Resource.CRYSTAL -> "💎"
                    }
                    Text(
                        "$emoji$amount",
                        fontSize = 12.sp,
                        color = if (enough) Color(0xFF4CAF50) else Color(0xFFFF6B6B)
                    )
                }
            }
            
            // Show what's missing if can't afford
            if (!canAfford) {
                val missing = structureType.cost.mapNotNull { (resource, needed) ->
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
            .width(320.dp)
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
                    "🔄 Trade Resources",
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
    val giveEmoji = when (giveResource) {
        Resource.MYCELIUM -> "🍄"
        Resource.BASALT -> "🧱"
        Resource.CHITIN -> "🪲"
        Resource.LICHEN -> "🌿"
        Resource.IRON_ORE -> "⚙️"
        Resource.CRYSTAL -> "💎"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D44))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "$giveEmoji Give $tradeRatio ${giveResource.displayName()} (have $playerAmount)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                "Receive 1:",
                color = Color.Gray,
                fontSize = 12.sp
            )
            
            // Row of receive options
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                items(allResources) { receiveResource ->
                    val receiveEmoji = when (receiveResource) {
                        Resource.MYCELIUM -> "🍄"
                        Resource.BASALT -> "🧱"
                        Resource.CHITIN -> "🪲"
                        Resource.LICHEN -> "🌿"
                        Resource.IRON_ORE -> "⚙️"
                        Resource.CRYSTAL -> "💎"
                    }
                    
                    Button(
                        onClick = { onTrade(receiveResource) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(receiveEmoji, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EventLog(events: List<String>, modifier: Modifier = Modifier) {
    // Compact vertical event log at the bottom of the screen
    Card(
        modifier = modifier.heightIn(max = 80.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x88000000))
    ) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(events.take(3)) { event ->
                Text(
                    "📜 $event",
                    color = Color.LightGray.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
    val (emoji, bgColor) = when (event) {
        is ExplorationEvent.TreasureCache -> "💎" to Color(0xFF4CAF50)
        is ExplorationEvent.CrystalVein -> "💎" to Color(0xFF00BCD4)
        is ExplorationEvent.AncientArtifact -> "🏺" to Color(0xFFFFD700)
        is ExplorationEvent.BeetleNest -> "🪲" to Color(0xFF8BC34A)
        is ExplorationEvent.FungalBloom -> "🍄" to Color(0xFF9C27B0)
        is ExplorationEvent.CaveIn -> "💥" to Color(0xFFF44336)
        is ExplorationEvent.GasLeak -> "☠️" to Color(0xFF9E9E9E)
        is ExplorationEvent.MagmaBurst -> "🌋" to Color(0xFFFF5722)
        is ExplorationEvent.Tremor -> "🌍" to Color(0xFF795548)
        is ExplorationEvent.GeothermalVent -> "♨️" to Color(0xFFFF9800)
        is ExplorationEvent.LostMiner -> "👷" to Color(0xFF2196F3)
        ExplorationEvent.StableGround -> "✓" to Color.Gray
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
            Text(emoji, fontSize = 48.sp)
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
            Text("🎲", fontSize = 36.sp)
            Spacer(Modifier.height(4.dp))
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
                        "${choice.emoji} ${choice.displayName}",
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
    onRestart: () -> Unit,
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
                    "🎉",
                    fontSize = 64.sp,
                    modifier = Modifier.scale(emojiScale)
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
                            "🏆 ${achievement.displayName}",
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
                    "📊 Lifetime: ${metaProgression.gamesWon}W/${metaProgression.gamesPlayed}G • ${metaProgression.totalVPEarned} VP",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))
                
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.scale(pulseScale(1f, 1.05f, 1000))
                ) {
                    Text("🔄 Play Again")
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
        modifier = modifier.width(180.dp),
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
                val riskInfo = when (tile.zone) {
                    Zone.SURFACE -> "🟢 Safe" to Color(0xFF4CAF50)
                    Zone.CRUST -> "🟡 Moderate risk" to Color(0xFFFFEB3B)
                    Zone.MANTLE -> "🟠 Risky — better rewards" to Color(0xFFFF9800)
                    Zone.CORE -> "🔴 Dangerous — richest rewards" to Color(0xFFF44336)
                }
                Text(
                    "❓ Unexplored — ${riskInfo.first}",
                    color = riskInfo.second,
                    fontSize = 14.sp
                )
                if (canExplore) {
                    Text(
                        "Tap to explore!",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp
                    )
                }
            } else {
                // Terrain type
                val (emoji, terrainName) = when (tile.terrain) {
                    TerrainType.FUNGAL_FOREST -> "🍄" to "Fungal Forest"
                    TerrainType.BASALT_QUARRY -> "🧱" to "Basalt Quarry"
                    TerrainType.BEETLE_FARM -> "🪲" to "Beetle Farm"
                    TerrainType.LICHEN_FIELD -> "🌿" to "Lichen Field"
                    TerrainType.IRON_VEIN -> "⚙️" to "Iron Vein"
                    TerrainType.CRYSTAL_GROTTO -> "💎" to "Crystal Grotto"
                    TerrainType.MAGMA_FLOW -> "🌋" to "Magma Flow"
                    TerrainType.BEDROCK -> "🪨" to "Bedrock"
                    TerrainType.UNKNOWN -> "?" to "Unknown"
                }
                
                Text(
                    "$emoji $terrainName",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                
                // Production number
                tile.numberToken?.let { number ->
                    Text(
                        "Produces on: $number",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
                
                // Status indicators
                if (tile.hasRubble) {
                    Text(
                        "⚠️ Has rubble",
                        color = Color(0xFFF44336),
                        fontSize = 12.sp
                    )
                }
                
                if (!tile.isIlluminated) {
                    Text(
                        "🌑 Not illuminated",
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
                    Text(
                        "🏗️ ${it.type.displayName}",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp
                    )
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
    
    return "Insufficient resources.\nMost needed: $mostNeeded\n\n💡 Try trading at $tradeRatio:1 ratio!"
}

@Composable
fun DifficultySelectionScreen(
    onSelectDifficulty: (Difficulty) -> Unit,
    metaProgression: MetaProgression = MetaProgression(),
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
                    "⛏️ SubTerrania ⛏️",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700)
                )
                
                Spacer(Modifier.height(8.dp))
                
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
                
                // Legend
                Text(
                    "💡 Higher difficulty = more hazards,\nfewer resources, harder trades",
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
                        "📊 ${metaProgression.gamesWon}/${metaProgression.gamesPlayed} wins • ${metaProgression.totalVPEarned} total VP • ${metaProgression.lifetimeAchievements.size} achievements",
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
    val (emoji, color, description) = when (difficulty) {
        Difficulty.EASY -> Triple("🌱", Color(0xFF4CAF50), "Relaxed mining • More resources • Forgiving")
        Difficulty.NORMAL -> Triple("⚒️", Color(0xFF2196F3), "Balanced experience • Standard rules")
        Difficulty.HARD -> Triple("💀", Color(0xFFFF9800), "Challenging • Scarce resources • More hazards")
        Difficulty.NIGHTMARE -> Triple("🔥", Color(0xFFF44336), "Brutal • Extreme scarcity • Constant danger")
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
            Text(
                emoji,
                fontSize = 32.sp
            )
            
            Spacer(Modifier.width(16.dp))
            
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
                    StatChip("🏆 ${difficulty.victoryPointsToWin} VP", Color.White)
                    StatChip("⚡ ${difficulty.maxActionsPerTurn}/turn", Color.White)
                    StatChip("💱 ${difficulty.tradeRatio}:1", Color.White)
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
