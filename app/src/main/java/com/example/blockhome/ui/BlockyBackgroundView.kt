package com.example.blockhome.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Original pixel-terrain wallpaper. Rendered once into a tiny bitmap and scaled up
 * without filtering, so it costs almost nothing to draw.
 */
class BlockyBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private val src = Rect()
    private val dst = Rect()
    private val paint = Paint().apply { isFilterBitmap = false }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bitmap?.recycle()
        bitmap = if (w > 0 && h > 0) render(w, h) else null
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        src.set(0, 0, bmp.width, bmp.height)
        dst.set(0, 0, width, height)
        canvas.drawBitmap(bmp, src, dst, paint)
    }

    private fun render(w: Int, h: Int): Bitmap {
        val cols = COLS
        val rows = max(1, (h.toFloat() * cols / w).roundToInt())
        val px = IntArray(cols * rows)
        val groundBase = rows * 0.66f

        for (x in 0 until cols) {
            val ground = (groundBase + 2.0 * sin(x * 0.35) + 1.5 * sin(x * 0.13 + 1.0)).roundToInt()
            for (y in 0 until rows) {
                val n = noise(x, y)
                px[y * cols + x] = when {
                    y < ground -> {
                        val band = (y * SKY.size / max(1, groundBase.toInt())).coerceIn(0, SKY.size - 1)
                        SKY[band]
                    }
                    y == ground -> if (n < 60) GRASS_DARK else GRASS
                    y < ground + 4 -> if (n < 40) DIRT_DARK else DIRT
                    else -> when {
                        n < 35 -> STONE_DARK
                        n > 230 -> STONE_LIGHT
                        else -> STONE
                    }
                }
            }
        }

        // Clouds: (column, row as % of sky height, length)
        val clouds = arrayOf(intArrayOf(3, 12, 7), intArrayOf(21, 28, 8), intArrayOf(11, 48, 6))
        for (c in clouds) {
            val r = (groundBase * c[1] / 100f).toInt()
            for (dx in 0 until c[2]) set(px, cols, rows, c[0] + dx, r + 1, CLOUD_SHADE)
            for (dx in 0 until c[2]) set(px, cols, rows, c[0] + dx, r, CLOUD)
            for (dx in 1 until c[2] - 1) set(px, cols, rows, c[0] + dx, r - 1, CLOUD)
        }

        return Bitmap.createBitmap(px, cols, rows, Bitmap.Config.ARGB_8888)
    }

    private fun set(px: IntArray, cols: Int, rows: Int, x: Int, y: Int, color: Int) {
        if (x in 0 until cols && y in 0 until rows) px[y * cols + x] = color
    }

    private fun noise(x: Int, y: Int): Int =
        (((x * 73856093) xor (y * 19349663)) ushr 3) and 0xFF

    private companion object {
        const val COLS = 36
        val SKY = intArrayOf(
            0xFF4F9EEB.toInt(), 0xFF64ABF0.toInt(), 0xFF7BB9F3.toInt(), 0xFF93C6F6.toInt()
        )
        val CLOUD = 0xFFFFFFFF.toInt()
        val CLOUD_SHADE = 0xFFDCE8F8.toInt()
        val GRASS = 0xFF5DAA3C.toInt()
        val GRASS_DARK = 0xFF4A8A2F.toInt()
        val DIRT = 0xFF8B5A2B.toInt()
        val DIRT_DARK = 0xFF6F4622.toInt()
        val STONE = 0xFF7D7D7D.toInt()
        val STONE_DARK = 0xFF666666.toInt()
        val STONE_LIGHT = 0xFF8F8F8F.toInt()
    }
}
