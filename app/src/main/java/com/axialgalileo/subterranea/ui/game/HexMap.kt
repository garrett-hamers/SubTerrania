package com.axialgalileo.subterranea.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.axialgalileo.subterranea.domain.model.HexCoordinate
import com.axialgalileo.subterranea.domain.model.HexTile
import com.axialgalileo.subterranea.domain.model.Zone
import kotlin.math.sqrt

@Composable
fun HexMap(
    board: Map<HexCoordinate, HexTile>,
    onTileClick: (HexCoordinate) -> Unit,
    modifier: Modifier = Modifier
) {
    val hexSize = 60f // Radius of hex
    
    // Calculate offsets to center the map (hardcoded for now, should be dynamic)
    val mapOffsetX = 500f
    val mapOffsetY = 1000f

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val clickedHex = pixelToHex(offset, hexSize, mapOffsetX, mapOffsetY)
                    onTileClick(clickedHex)
                }
            }
    ) {
        board.forEach { (coord, tile) ->
            val center = hexToPixel(coord, hexSize, mapOffsetX, mapOffsetY)
            val path = createHexPath(center, hexSize)
            
            // Color based on Zone or Reveal status
            val fillColor = when {
                !tile.isRevealed -> Color.DarkGray
                tile.zone == Zone.SURFACE -> Color(0xFF8D6E63) // Brown
                tile.zone == Zone.CRUST -> Color(0xFF7CB342)  // Greenish
                else -> Color.Black
            }
            
            drawPath(
                path = path,
                color = fillColor,
            )
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 2f)
            )
        }
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
