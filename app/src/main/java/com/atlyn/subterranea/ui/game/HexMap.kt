package com.atlyn.subterranea.ui.game

import android.graphics.Paint
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import com.atlyn.subterranea.R
import com.atlyn.subterranea.domain.model.*
import com.atlyn.subterranea.ui.util.IconHelper
import kotlin.math.sqrt

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

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Use dynamic offsets for click detection
                    val mapOffsetX = size.width / 2f
                    val mapOffsetY = size.height / 2f - 50f
                    val clickedHex = pixelToHex(offset, hexSize, mapOffsetX, mapOffsetY)
                    onTileClick(clickedHex)
                }
            }
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
