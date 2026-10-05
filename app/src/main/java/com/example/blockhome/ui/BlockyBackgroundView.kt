package com.example.blockhome.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * BlockHome background.
 *
 * By default this renders the original lightweight pixel terrain.
 * A user-selected local image can be used instead.
 */
class BlockyBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private var customBitmap: Bitmap? = null

    private val src = Rect()
    private val dst = Rect()

    private val paint = Paint().apply {
        isFilterBitmap = false
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)

        bitmap?.recycle()
        bitmap = null

        if (w > 0 && h > 0 && customBitmap == null) {
            bitmap = render(w, h)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val custom = customBitmap

        if (custom != null && !custom.isRecycled) {
            drawCover(canvas, custom)
            return
        }

        val defaultBitmap = bitmap ?: return

        src.set(
            0,
            0,
            defaultBitmap.width,
            defaultBitmap.height
        )

        dst.set(
            0,
            0,
            width,
            height
        )

        canvas.drawBitmap(
            defaultBitmap,
            src,
            dst,
            paint
        )
    }

    fun setCustomBackground(uri: Uri) {
        val resolver = context.contentResolver

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inScaled = true
        }

        val loaded = try {
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(
                    input,
                    null,
                    options
                )
            }
        } catch (_: Exception) {
            null
        }

        if (loaded == null) {
            return
        }

        customBitmap?.recycle()
        customBitmap = loaded

        bitmap?.recycle()
        bitmap = null

        invalidate()
    }

    fun setDefaultBackground() {
        customBitmap?.recycle()
        customBitmap = null

        bitmap?.recycle()
        bitmap = null

        if (width > 0 && height > 0) {
            bitmap = render(width, height)
        }

        invalidate()
    }

    fun release() {
        customBitmap?.recycle()
        customBitmap = null

        bitmap?.recycle()
        bitmap = null
    }

    private fun drawCover(
        canvas: Canvas,
        source: Bitmap
    ) {
        val sourceWidth = source.width.toFloat()
        val sourceHeight = source.height.toFloat()

        if (
            sourceWidth <= 0f ||
            sourceHeight <= 0f ||
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        val scale = max(
            width / sourceWidth,
            height / sourceHeight
        )

        val drawWidth = (sourceWidth * scale).roundToInt()
        val drawHeight = (sourceHeight * scale).roundToInt()

        val left = (width - drawWidth) / 2
        val top = (height - drawHeight) / 2

        src.set(
            0,
            0,
            source.width,
            source.height
        )

        dst.set(
            left,
            top,
            left + drawWidth,
            top + drawHeight
        )

        canvas.drawBitmap(
            source,
            src,
            dst,
            paint
        )
    }

    private fun render(
        w: Int,
        h: Int
    ): Bitmap {
        val cols = COLS

        val rows = max(
            1,
            (h.toFloat() * cols / w).roundToInt()
        )

        val px = IntArray(cols * rows)

        val groundBase = rows * 0.66f

        for (x in 0 until cols) {
            val ground =
                (
                    groundBase +
                        2.0 * sin(x * 0.35) +
                        1.5 * sin(x * 0.13 + 1.0)
                    ).roundToInt()

            for (y in 0 until rows) {
                val n = noise(x, y)

                px[y * cols + x] =
                    when {
                        y < ground -> {
                            val band =
                                (
                                    y *
                                        SKY.size /
                                        max(
                                            1,
                                            groundBase.toInt()
                                        )
                                    ).coerceIn(
                                        0,
                                        SKY.size - 1
                                    )

                            SKY[band]
                        }

                        y == ground ->
                            if (n < 60) {
                                GRASS_DARK
                            } else {
                                GRASS
                            }

                        y < ground + 4 ->
                            if (n < 40) {
                                DIRT_DARK
                            } else {
                                DIRT
                            }

                        else ->
                            when {
                                n < 35 -> STONE_DARK
                                n > 230 -> STONE_LIGHT
                                else -> STONE
                            }
                    }
            }
        }

        val clouds = arrayOf(
            intArrayOf(3, 12, 7),
            intArrayOf(21, 28, 8),
            intArrayOf(11, 48, 6)
        )

        for (cloud in clouds) {
            val row =
                (groundBase * cloud[1] / 100f).toInt()

            for (dx in 0 until cloud[2]) {
                set(
                    px,
                    cols,
                    rows,
                    cloud[0] + dx,
                    row + 1,
                    CLOUD_SHADE
                )
            }

            for (dx in 0 until cloud[2]) {
                set(
                    px,
                    cols,
                    rows,
                    cloud[0] + dx,
                    row,
                    CLOUD
                )
            }

            for (dx in 1 until cloud[2] - 1) {
                set(
                    px,
                    cols,
                    rows,
                    cloud[0] + dx,
                    row - 1,
                    CLOUD
                )
            }
        }

        return Bitmap.createBitmap(
            px,
            cols,
            rows,
            Bitmap.Config.ARGB_8888
        )
    }

    private fun set(
        px: IntArray,
        cols: Int,
        rows: Int,
        x: Int,
        y: Int,
        color: Int
    ) {
        if (
            x in 0 until cols &&
            y in 0 until rows
        ) {
            px[y * cols + x] = color
        }
    }

    private fun noise(
        x: Int,
        y: Int
    ): Int =
        (
            (
                (x * 73856093) xor
                    (y * 19349663)
                ) ushr 3
            ) and 0xFF

    private companion object {
        const val COLS = 36

        val SKY = intArrayOf(
            0xFF4F9EEB.toInt(),
            0xFF64ABF0.toInt(),
            0xFF7BB9F3.toInt(),
            0xFF93C6F6.toInt()
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
