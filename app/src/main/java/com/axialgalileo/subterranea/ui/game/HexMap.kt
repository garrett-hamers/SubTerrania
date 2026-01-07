package com.axialgalileo.subterranea.ui.game

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
import com.axialgalileo.subterranea.domain.model.*
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
                    color = Color(0xFF00FF00).copy(alpha = 0.8f),
                    style = Stroke(width = 5f)
                )
                // Draw "explore" hint
                drawCircle(
                    color = Color(0xFF00FF00).copy(alpha = 0.3f),
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
                drawTileContent(center, tile, hexSize)
            } else {
                // Draw fog/unknown indicator
                drawCircle(
                    color = Color.DarkGray.copy(alpha = 0.5f),
                    radius = hexSize * 0.3f,
                    center = center
                )
                drawContext.canvas.nativeCanvas.drawText(
                    if (coord in explorableTiles) "👆" else "?",
                    center.x,
                    center.y + 8f,
                    android.graphics.Paint().apply {
                        color = if (coord in explorableTiles) android.graphics.Color.WHITE else android.graphics.Color.GRAY
                        textSize = if (coord in explorableTiles) 32f else 24f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
        
        // Draw structures on top
        structures.forEach { structure ->
            val center = hexToPixel(structure.location, hexSize, mapOffsetX, mapOffsetY)
            drawStructure(center, structure, hexSize)
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

private fun DrawScope.drawTileContent(center: Offset, tile: HexTile, hexSize: Float) {
    // Draw terrain emoji/icon
    val emoji = when (tile.terrain) {
        TerrainType.FUNGAL_FOREST -> "🍄"
        TerrainType.BASALT_QUARRY -> "🧱"
        TerrainType.BEETLE_FARM -> "🪲"
        TerrainType.LICHEN_FIELD -> "🌿"
        TerrainType.IRON_VEIN -> "⚙️"
        TerrainType.CRYSTAL_GROTTO -> "💎"
        TerrainType.MAGMA_FLOW -> "🌋"
        TerrainType.BEDROCK -> "🪨"
        TerrainType.UNKNOWN -> "?"
    }
    
    drawContext.canvas.nativeCanvas.drawText(
        emoji,
        center.x,
        center.y - hexSize * 0.15f,
        android.graphics.Paint().apply {
            textSize = hexSize * 0.5f
            textAlign = android.graphics.Paint.Align.CENTER
        }
    )
    
    // Draw number token
    tile.numberToken?.let { number ->
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
            center = Offset(center.x, center.y + hexSize * 0.35f)
        )
        
        drawContext.canvas.nativeCanvas.drawText(
            number.toString(),
            center.x,
            center.y + hexSize * 0.42f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = hexSize * 0.3f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
        )
    }
    
    // Draw rubble indicator
    if (tile.hasRubble) {
        drawContext.canvas.nativeCanvas.drawText(
            "⚠️",
            center.x + hexSize * 0.3f,
            center.y - hexSize * 0.3f,
            android.graphics.Paint().apply {
                textSize = hexSize * 0.3f
                textAlign = android.graphics.Paint.Align.CENTER
            }
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

private fun DrawScope.drawStructure(center: Offset, structure: Structure, hexSize: Float) {
    val (emoji, bgColor) = when (structure.type) {
        StructureType.LANTERN -> "💡" to Color(0xFFFFEB3B)
        StructureType.OUTPOST -> "🏠" to Color(0xFF8D6E63)
        StructureType.EXCAVATOR -> "⛏️" to Color(0xFF795548)
        StructureType.FUNGAL_FARM -> "🏭" to Color(0xFF7B1FA2)
        StructureType.BEETLE_STABLE -> "🐛" to Color(0xFF388E3C)
        StructureType.CRYSTAL_REFINERY -> "🏢" to Color(0xFF00ACC1)
        StructureType.CORE_ANCHOR -> "⚓" to Color(0xFFD84315)
    }
    
    // Draw structure background
    drawCircle(
        color = bgColor,
        radius = hexSize * 0.35f,
        center = Offset(center.x, center.y - hexSize * 0.1f)
    )
    
    drawCircle(
        color = Color.Black,
        radius = hexSize * 0.35f,
        center = Offset(center.x, center.y - hexSize * 0.1f),
        style = Stroke(width = 2f)
    )
    
    // Draw structure icon
    drawContext.canvas.nativeCanvas.drawText(
        emoji,
        center.x,
        center.y,
        android.graphics.Paint().apply {
            textSize = hexSize * 0.4f
            textAlign = android.graphics.Paint.Align.CENTER
        }
    )
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
