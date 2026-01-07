package com.axialgalileo.subterranea.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Animation utilities for tactile game feel
 */

/**
 * Creates a bouncy scale animation when triggered
 */
@Composable
fun rememberBounceState(): BounceState {
    return remember { BounceState() }
}

class BounceState {
    var isAnimating by mutableStateOf(false)
        private set
    
    fun trigger() {
        isAnimating = true
    }
    
    fun onAnimationComplete() {
        isAnimating = false
    }
}

/**
 * Modifier that applies a bounce scale animation
 */
fun Modifier.bounceOnTrigger(
    bounceState: BounceState,
    scaleUp: Float = 1.15f,
    scaleDown: Float = 0.95f
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = when {
            bounceState.isAnimating -> scaleUp
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        finishedListener = { bounceState.onAnimationComplete() },
        label = "bounce"
    )
    
    this.scale(scale)
}

/**
 * Shake animation modifier for dice or error feedback
 */
fun Modifier.shakeAnimation(
    enabled: Boolean,
    intensity: Float = 10f
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -intensity,
        targetValue = intensity,
        animationSpec = infiniteRepeatable(
            animation = tween(50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shakeX"
    )
    
    if (enabled) {
        this.offset { IntOffset(offsetX.roundToInt(), 0) }
    } else {
        this
    }
}

/**
 * Pulse animation for highlighting important elements
 */
@Composable
fun pulseAlpha(
    minAlpha: Float = 0.5f,
    maxAlpha: Float = 1f,
    durationMillis: Int = 800
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    return alpha
}

/**
 * Pulsing scale animation for buttons or important UI elements
 */
@Composable
fun pulseScale(
    minScale: Float = 1f,
    maxScale: Float = 1.08f,
    durationMillis: Int = 600
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseScale")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScaleValue"
    )
    return scale
}

/**
 * Dice rolling animation state
 */
class DiceAnimationState {
    var isRolling by mutableStateOf(false)
        private set
    
    var displayValue1 by mutableStateOf(1)
        private set
    var displayValue2 by mutableStateOf(1)
        private set
    
    var finalValue1: Int = 1
        private set
    var finalValue2: Int = 1
        private set
    
    fun startRoll(final1: Int, final2: Int) {
        isRolling = true
        finalValue1 = final1
        finalValue2 = final2
    }
    
    fun updateDisplay(val1: Int, val2: Int) {
        displayValue1 = val1
        displayValue2 = val2
    }
    
    fun finishRoll() {
        displayValue1 = finalValue1
        displayValue2 = finalValue2
        isRolling = false
    }
}

@Composable
fun rememberDiceAnimationState(): DiceAnimationState {
    return remember { DiceAnimationState() }
}

/**
 * Glow effect animation for selected/highlighted elements
 */
@Composable
fun glowAnimation(
    enabled: Boolean,
    minGlow: Float = 0.3f,
    maxGlow: Float = 1f,
    durationMillis: Int = 1000
): Float {
    if (!enabled) return 0f
    
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glow by infiniteTransition.animateFloat(
        initialValue = minGlow,
        targetValue = maxGlow,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowValue"
    )
    return glow
}

/**
 * Float up animation for showing gained resources
 */
@Composable
fun floatUpAnimation(
    triggered: Boolean,
    durationMillis: Int = 1500,
    onComplete: () -> Unit = {}
): Pair<Float, Float> {
    var offsetY by remember { mutableFloatStateOf(0f) }
    var alpha by remember { mutableFloatStateOf(1f) }
    
    LaunchedEffect(triggered) {
        if (triggered) {
            offsetY = 0f
            alpha = 1f
            // Animate over duration
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < durationMillis) {
                val progress = (System.currentTimeMillis() - startTime) / durationMillis.toFloat()
                offsetY = -100f * progress
                alpha = 1f - progress
                kotlinx.coroutines.delay(16)
            }
            onComplete()
        }
    }
    
    return offsetY to alpha
}

/**
 * Rotation animation for spinning elements
 */
fun Modifier.spinAnimation(
    enabled: Boolean,
    durationMillis: Int = 150
): Modifier = composed {
    val rotation by animateFloatAsState(
        targetValue = if (enabled) 360f else 0f,
        animationSpec = tween(durationMillis, easing = LinearEasing),
        label = "spin"
    )
    
    this.graphicsLayer { rotationZ = rotation }
}

/**
 * Pop-in animation for newly appearing elements
 */
fun Modifier.popInAnimation(
    visible: Boolean,
    delayMillis: Int = 0
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "popIn"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(200),
        label = "popInAlpha"
    )
    
    this
        .scale(scale)
        .graphicsLayer { this.alpha = alpha }
}

/**
 * Wiggle animation for attention-grabbing elements
 */
fun Modifier.wiggleAnimation(
    enabled: Boolean
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "wiggle")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wiggleRotation"
    )
    
    if (enabled) {
        this.graphicsLayer { rotationZ = rotation }
    } else {
        this
    }
}
