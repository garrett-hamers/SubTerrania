package com.atlyn.subterranea.ui.game

import android.graphics.Paint
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import com.atlyn.subterranea.R
import com.atlyn.subterranea.domain.model.*
import com.atlyn.subterranea.ui.util.IconHelper
import kotlin.math.sqrt

// Zoom limits — extracted as top-level constants so unit tests can reference them.
const val HEX_MAP_MIN_SCALE = 0.7f
const val HEX_MAP_MAX_SCALE = 3.0f

@Composable
fun HexMap(
    board: Map<HexCoordinate, HexTile>,
    structures: List<Structure> = emptyList(),
    selectedTile: HexCoordinate? = null,
    explorableTiles: Set<HexCoordinate> = emptySet(),
    buildableTiles: Set<HexCoordinate> = emptySet(),
    onTileClick: (HexCoordinate) -> Unit,
    modifier: Modifier = Modifier
) {
    val hexSize = 70f // Radius of hex - larger for better visibility
    val context = LocalContext.current
    val boardIconSizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val badgeBitmaps = remember {
        val badgeSize = 32
        mapOf(
            Zone.SURFACE to BitmapFactory.decodeResource(context.resources, R.drawable.ic_badge_safe)?.let {
                android.graphics.Bitmap.createScaledBitmap(it, badgeSize, badgeSize, true)
            },
            Zone.CRUST to BitmapFactory.decodeResource(context.resources, R.drawable.ic_badge_moderate)?.let {
                android.graphics.Bitmap.createScaledBitmap(it, badgeSize, badgeSize, true)
            },
            Zone.MANTLE to BitmapFactory.decodeResource(context.resources, R.drawable.ic_badge_risky)?.let {
                android.graphics.Bitmap.createScaledBitmap(it, badgeSize, badgeSize, true)
            },
            Zone.CORE to BitmapFactory.decodeResource(context.resources, R.drawable.ic_badge_dangerous)?.let {
                android.graphics.Bitmap.createScaledBitmap(it, badgeSize, badgeSize, true)
            }
        )
    }
    val terrainBitmaps = remember(boardIconSizePx) {
        val iconSize = boardIconSizePx
        TerrainType.entries.associateWith { terrain ->
            BitmapFactory.decodeResource(context.resources, IconHelper.getTerrainIconRes(terrain))?.let {
                android.graphics.Bitmap.createScaledBitmap(it, iconSize, iconSize, true)
            }
        }
    }
    val structureBitmaps = remember(boardIconSizePx) {
        val iconSize = boardIconSizePx
        StructureType.entries.associateWith { structure ->
            BitmapFactory.decodeResource(context.resources, IconHelper.getStructureIconRes(structure))?.let {
                android.graphics.Bitmap.createScaledBitmap(it, iconSize, iconSize, true)
            }
        }
    }
    val fogLabelPaint = remember {
        Paint().apply {
            textAlign = Paint.Align.CENTER
        }
    }
    val terrainLabelPaint = remember {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    val numberTextPaint = remember {
        Paint().apply {
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    val rubblePaint = remember {
        Paint().apply {
            textAlign = Paint.Align.CENTER
        }
    }
    val structureLabelPaint = remember {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    }

    // Pulse animation for explorable tiles
    val infiniteTransition = rememberInfiniteTransition(label = "hexPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    val pulseWidth by infiniteTransition.animateFloat(
        initialValue = 5f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseWidth"
    )

    // ---- Zoom + pan state (session-only; not persisted) ----
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                // Pinch + drag handler. Placed BEFORE graphicsLayer so it
                // receives events in the layer's outer (untransformed) coords.
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = clampScale(oldScale * zoom)
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val cx = w / 2f
                        val cy = h / 2f
                        // Keep the canvas-local point under the pinch focal pinned to the same screen pixel.
                        val focalLocalX = (centroid.x - cx - offset.x) / oldScale + cx
                        val focalLocalY = (centroid.y - cy - offset.y) / oldScale + cy
                        val newOffset = Offset(
                            x = centroid.x - cx - (focalLocalX - cx) * newScale + pan.x,
                            y = centroid.y - cy - (focalLocalY - cy) * newScale + pan.y
                        )
                        scale = newScale
                        offset = clampOffset(newOffset, newScale, IntSize(size.width, size.height))
                    }
                }
                // Tap handler (must invert the visual transform to find the hex the user actually touched).
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val local = screenToCanvasLocal(tap, scale, offset, IntSize(size.width, size.height))
                        val mapOffsetX = w / 2f
                        val mapOffsetY = h / 2f - 50f
                        val clickedHex = pixelToHex(local, hexSize, mapOffsetX, mapOffsetY)
                        onTileClick(clickedHex)
                    }
                }
                // Render with the live, un-animated state so what the user sees
                // always matches the transform used by gesture math + tap inversion.
                // Button-driven changes (+/-/recenter) will snap rather than animate
                // — we accepted that trade-off for hit-testing correctness.
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    transformOrigin = TransformOrigin.Center
                )
        ) {
            // Center the map dynamically based on canvas size
            val mapOffsetX = size.width / 2f
            val mapOffsetY = size.height / 2f - 50f
            
            // Draw all hex tiles
            board.forEach { (coord, tile) ->
                val center = hexToPixel(coord, hexSize, mapOffsetX, mapOffsetY)
                val path = createHexPath(center, hexSize)
                
                // Get fill color based on state
                val fillColor = getTileColor(tile)
                
                // Draw hex fill
                drawPath(
                    path = path,
                    color = fillColor,
                )
                
                // Draw explorable tile indicator (pulsing green border)
                if (coord in explorableTiles) {
                    drawPath(
                        path = path,
                        color = Color(0xFF00FF00).copy(alpha = pulseAlpha),
                        style = Stroke(width = pulseWidth)
                    )
                    // Draw "explore" hint
                    drawCircle(
                        color = Color(0xFF00FF00).copy(alpha = pulseAlpha * 0.3f),
                        radius = hexSize * 0.8f,
                        center = center
                    )
                }
                
                // Draw buildable tile indicator (blue glow)
                if (coord in buildableTiles && coord !in explorableTiles) {
                    drawPath(
                        path = path,
                        color = Color(0xFF2196F3).copy(alpha = 0.8f),
                        style = Stroke(width = 4f)
                    )
                    drawCircle(
                        color = Color(0xFF2196F3).copy(alpha = 0.2f),
                        radius = hexSize * 0.7f,
                        center = center
                    )
                }
                
                // Draw selection highlight (on top of other indicators)
                if (coord == selectedTile) {
                    drawPath(
                        path = path,
                        color = Color(0xFFFFD700),
                        style = Stroke(width = 6f)
                    )
                }
                
                // Draw zone border (different colors per zone) - only if not highlighted
                if (coord != selectedTile && coord !in explorableTiles && coord !in buildableTiles) {
                    val borderColor = when (tile.zone) {
                        Zone.SURFACE -> Color(0xFF4CAF50)
                        Zone.CRUST -> Color(0xFF8BC34A)
                        Zone.MANTLE -> Color(0xFFFF9800)
                        Zone.CORE -> Color(0xFFF44336)
                    }
                    drawPath(
                        path = path,
                        color = borderColor,
                        style = Stroke(width = 2f)
                    )
                }
                
                // Draw tile content if revealed
                if (tile.isRevealed) {
                    drawTileContent(
                        center = center,
                        tile = tile,
                        hexSize = hexSize,
                        terrainBitmaps = terrainBitmaps,
                        terrainLabelPaint = terrainLabelPaint,
                        numberTextPaint = numberTextPaint,
                        rubblePaint = rubblePaint
                    )
                } else {
                    // Draw fog/unknown indicator
                    drawCircle(
                        color = Color.DarkGray.copy(alpha = 0.5f),
                        radius = hexSize * 0.3f,
                        center = center
                    )
                    // Show risk indicator for explorable tiles, "?" for others
                    if (coord in explorableTiles) {
                        val badge = badgeBitmaps[tile.zone]
                        if (badge != null) {
                            drawContext.canvas.nativeCanvas.drawBitmap(
                                badge,
                                center.x - badge.width / 2f,
                                center.y - badge.height / 2f,
                                null
                            )
                        }
                    } else {
                        fogLabelPaint.color = android.graphics.Color.GRAY
                        fogLabelPaint.textSize = 24f
                        drawContext.canvas.nativeCanvas.drawText(
                            "?",
                            center.x,
                            center.y + 8f,
                            fogLabelPaint
                        )
                    }
                }
            }
            
            // Draw structures on top
            structures.forEach { structure ->
                val center = hexToPixel(structure.location, hexSize, mapOffsetX, mapOffsetY)
                drawStructure(center, structure, hexSize, structureBitmaps, structureLabelPaint)
            }
        }

        // Floating zoom controls (a11y alternative to pinch).
        ZoomControls(
            onZoomIn = {
                val newScale = clampScale(scale * 1.25f)
                scale = newScale
                offset = clampOffset(offset, newScale, canvasSize)
            },
            onZoomOut = {
                val newScale = clampScale(scale * 0.8f)
                scale = newScale
                offset = clampOffset(offset, newScale, canvasSize)
            },
            onRecenter = {
                scale = 1f
                offset = Offset.Zero
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
        )
    }
}

// ---- Pure helpers (testable, no Compose runtime) ----

/** Clamp [scale] to the supported zoom range. */
fun clampScale(scale: Float): Float = scale.coerceIn(HEX_MAP_MIN_SCALE, HEX_MAP_MAX_SCALE)

/**
 * Inner pan-clamp logic, expressed in primitives so it can be unit-tested
 * without depending on Compose's Offset/IntSize types. See [clampOffset] for
 * the rule (≥25% of the viewport must show board content).
 */
fun clampOffsetXY(
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    canvasWidth: Int,
    canvasHeight: Int
): Pair<Float, Float> {
    if (canvasWidth <= 0 || canvasHeight <= 0) return offsetX to offsetY
    val w = canvasWidth.toFloat()
    val h = canvasHeight.toFloat()
    val maxX = w * (0.25f + scale / 2f)
    val maxY = h * (0.25f + scale / 2f)
    return offsetX.coerceIn(-maxX, maxX) to offsetY.coerceIn(-maxY, maxY)
}

/**
 * Inner inverse-transform logic, expressed in primitives. See
 * [screenToCanvasLocal] for the geometry.
 */
fun screenToCanvasLocalXY(
    screenX: Float,
    screenY: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    canvasWidth: Int,
    canvasHeight: Int
): Pair<Float, Float> {
    if (scale == 0f) return screenX to screenY
    val cx = canvasWidth / 2f
    val cy = canvasHeight / 2f
    return ((screenX - cx - offsetX) / scale + cx) to
            ((screenY - cy - offsetY) / scale + cy)
}

/**
 * Clamp pan [offset] so that at least 25% of the canvas viewport keeps showing
 * board content. With center transform origin and scale [scale], the transformed
 * content fills [(W/2)(1-s)+tx, (W/2)(1+s)+tx] horizontally; similarly vertically.
 * Requiring overlap >= 25% of the viewport gives bounds:
 *   |tx| <= W * (0.25 + s/2)
 *   |ty| <= H * (0.25 + s/2)
 */
fun clampOffset(offset: Offset, scale: Float, canvasSize: IntSize): Offset {
    val (x, y) = clampOffsetXY(offset.x, offset.y, scale, canvasSize.width, canvasSize.height)
    return Offset(x, y)
}

/**
 * Inverse of the graphicsLayer transform applied to the HexMap canvas. Takes a
 * point in the canvas's outer (untransformed) coordinate system — which is what
 * pointerInput handlers receive — and returns the corresponding point in the
 * canvas's local drawing coordinate system, which is what hexToPixel/pixelToHex
 * operate on.
 */
fun screenToCanvasLocal(screen: Offset, scale: Float, offset: Offset, canvasSize: IntSize): Offset {
    val (x, y) = screenToCanvasLocalXY(
        screen.x, screen.y, scale, offset.x, offset.y, canvasSize.width, canvasSize.height
    )
    return Offset(x, y)
}

@Composable
private fun ZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        ZoomButton(label = "+", description = "Zoom in", onClick = onZoomIn)
        ZoomButton(label = "−", description = "Zoom out", onClick = onZoomOut)
        ZoomButton(label = "⊙", description = "Recenter board", onClick = onRecenter)
    }
}

@Composable
private fun ZoomButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color(0xCC111827), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(0xFF29B6F6),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun getTileColor(tile: HexTile): Color {
    return when {
        !tile.isRevealed -> Color(0xFF2D2D44) // Fog of war
        tile.hasRubble -> Color(0xFF5D4037) // Rubble brown
        tile.terrain == TerrainType.MAGMA_FLOW -> Color(0xFFD84315) // Magma orange-red
        tile.terrain == TerrainType.BEDROCK -> Color(0xFF424242) // Dark gray
        !tile.isIlluminated -> {
            // Dimmed version of terrain color
            getTerrainColor(tile.terrain).copy(alpha = 0.4f)
        }
        else -> getTerrainColor(tile.terrain)
    }
}

private fun getTerrainColor(terrain: TerrainType): Color {
    return when (terrain) {
        TerrainType.UNKNOWN -> Color(0xFF2D2D44)
        TerrainType.FUNGAL_FOREST -> Color(0xFF7B1FA2) // Purple
        TerrainType.BASALT_QUARRY -> Color(0xFF5D4037) // Brown
        TerrainType.BEETLE_FARM -> Color(0xFF388E3C) // Green
        TerrainType.LICHEN_FIELD -> Color(0xFF689F38) // Light green
        TerrainType.IRON_VEIN -> Color(0xFF546E7A) // Blue-gray
        TerrainType.CRYSTAL_GROTTO -> Color(0xFF00ACC1) // Cyan
        TerrainType.MAGMA_FLOW -> Color(0xFFD84315) // Orange
        TerrainType.BEDROCK -> Color(0xFF424242) // Gray
    }
}

private fun DrawScope.drawTileContent(
    center: Offset,
    tile: HexTile,
    hexSize: Float,
    terrainBitmaps: Map<TerrainType, android.graphics.Bitmap?>,
    terrainLabelPaint: Paint,
    numberTextPaint: Paint,
    rubblePaint: Paint
) {
    // Draw terrain icon with text fallback
    val terrainBitmap = terrainBitmaps[tile.terrain]
    if (terrainBitmap != null) {
        drawContext.canvas.nativeCanvas.drawBitmap(
            terrainBitmap,
            center.x - terrainBitmap.width / 2f,
            center.y - hexSize * 0.68f,
            null
        )
    } else {
        terrainLabelPaint.color = android.graphics.Color.WHITE
        terrainLabelPaint.textSize = hexSize * 0.26f
        drawContext.canvas.nativeCanvas.drawText(
            tile.terrain.displayName().take(2).uppercase(),
            center.x,
            center.y - hexSize * 0.24f,
            terrainLabelPaint
        )
    }
    
    // Draw number token(s)
    tile.numberToken?.let { number ->
        val secondary = tile.secondaryNumberToken
        // Background circle for number
        val numberColor = when (number) {
            6, 8 -> Color(0xFFFF5722) // High probability - red
            5, 9 -> Color(0xFFFF9800) // Medium-high - orange
            4, 10 -> Color(0xFFFFEB3B) // Medium - yellow
            else -> Color.White // Low probability
        }
        
        drawCircle(
            color = numberColor,
            radius = hexSize * 0.25f,
            center = Offset(center.x, center.y + hexSize * 0.45f)
        )
        
        val displayText = if (secondary != null) "$number|$secondary" else number.toString()
        val fontSize = if (secondary != null) hexSize * 0.22f else hexSize * 0.3f
        
        numberTextPaint.textSize = fontSize
        drawContext.canvas.nativeCanvas.drawText(
            displayText,
            center.x,
            center.y + hexSize * 0.53f,
            numberTextPaint
        )
    }
    
    // Draw rubble indicator
    if (tile.hasRubble) {
        val rubbleCenter = Offset(center.x + hexSize * 0.32f, center.y - hexSize * 0.3f)
        drawCircle(
            color = Color(0xFFE65100),
            radius = hexSize * 0.15f,
            center = rubbleCenter
        )
        rubblePaint.color = android.graphics.Color.WHITE
        rubblePaint.textSize = hexSize * 0.24f
        rubblePaint.isFakeBoldText = true
        drawContext.canvas.nativeCanvas.drawText(
            "!",
            rubbleCenter.x,
            rubbleCenter.y + hexSize * 0.08f,
            rubblePaint
        )
    }
    
    // Draw illumination indicator (subtle glow)
    if (tile.isIlluminated && tile.terrain.produces != null) {
        drawCircle(
            color = Color(0x33FFFF00), // Subtle yellow glow
            radius = hexSize * 0.9f,
            center = center
        )
    }
}

private fun DrawScope.drawStructure(
    center: Offset,
    structure: Structure,
    hexSize: Float,
    structureBitmaps: Map<StructureType, android.graphics.Bitmap?>,
    structureLabelPaint: Paint
) {
    val bgColor = when (structure.type) {
        StructureType.LANTERN -> Color(0xFFFFEB3B)
        StructureType.OUTPOST -> Color(0xFF8D6E63)
        StructureType.EXCAVATOR -> Color(0xFF795548)
        StructureType.FUNGAL_FARM -> Color(0xFF7B1FA2)
        StructureType.BEETLE_STABLE -> Color(0xFF388E3C)
        StructureType.CRYSTAL_REFINERY -> Color(0xFF00ACC1)
        StructureType.CORE_ANCHOR -> Color(0xFFD84315)
    }
    val iconCenter = Offset(center.x, center.y - hexSize * 0.22f)
    
    // Draw structure background
    drawCircle(
        color = bgColor,
        radius = hexSize * 0.35f,
        center = iconCenter
    )
    
    drawCircle(
        color = Color.Black,
        radius = hexSize * 0.35f,
        center = iconCenter,
        style = Stroke(width = 2f)
    )
    
    val structureBitmap = structureBitmaps[structure.type]
    if (structureBitmap != null) {
        drawContext.canvas.nativeCanvas.drawBitmap(
            structureBitmap,
            iconCenter.x - structureBitmap.width / 2f,
            iconCenter.y - structureBitmap.height / 2f,
            null
        )
    } else {
        structureLabelPaint.color = android.graphics.Color.WHITE
        structureLabelPaint.textSize = hexSize * 0.28f
        drawContext.canvas.nativeCanvas.drawText(
            structure.type.displayName.take(1).uppercase(),
            iconCenter.x,
            iconCenter.y + hexSize * 0.1f,
            structureLabelPaint
        )
    }
}

fun createHexPath(center: Offset, size: Float): Path {
    val path = Path()
    for (i in 0..5) {
        val angle_deg = 60 * i + 30
        val angle_rad = Math.PI / 180 * angle_deg
        val x = center.x + size * Math.cos(angle_rad).toFloat()
        val y = center.y + size * Math.sin(angle_rad).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

fun hexToPixel(hex: HexCoordinate, size: Float, offsetX: Float, offsetY: Float): Offset {
    val x = size * (sqrt(3.0) * hex.q + sqrt(3.0)/2 * hex.r)
    val y = size * (3.0/2 * hex.r)
    return Offset(x.toFloat() + offsetX, y.toFloat() + offsetY)
}

fun pixelToHex(point: Offset, size: Float, offsetX: Float, offsetY: Float): HexCoordinate {
    val px = point.x - offsetX
    val py = point.y - offsetY
    
    val q = (sqrt(3.0)/3 * px - 1.0/3 * py) / size
    val r = (2.0/3 * py) / size
    return axialRound(q, r)
}

fun axialRound(q: Double, r: Double): HexCoordinate {
    var rx = Math.round(q).toInt()
    var ry = Math.round(r).toInt()
    var rz = Math.round(-q - r).toInt()

    val x_diff = Math.abs(rx - q)
    val y_diff = Math.abs(ry - r)
    val z_diff = Math.abs(rz - (-q - r))

    if (x_diff > y_diff && x_diff > z_diff) {
        rx = -ry - rz
    } else if (y_diff > z_diff) {
        ry = -rx - rz
    }
    
    return HexCoordinate(rx, ry)
}
