package com.example.blockhome.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
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
    private var activeTheme: String = "Grass & Dirt"

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

    fun setTheme(theme: String) {
        activeTheme = theme
        if (customBitmap == null && width > 0 && height > 0) {
            bitmap?.recycle()
            bitmap = render(width, height)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(
                if (theme == "Glass") RenderEffect.createBlurEffect(7f, 7f, Shader.TileMode.CLAMP)
                else null
            )
        }
        invalidate()
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

                px[y * cols + x] = when {
                    y < ground -> {
                        val sky = when (activeTheme) {
                            "Stone" -> intArrayOf(0xFF777F89.toInt(), 0xFF929AA3.toInt(), 0xFFADB4BC.toInt(), 0xFFC8CDD2.toInt())
                            "Diamond" -> intArrayOf(0xFF07566B.toInt(), 0xFF087C99.toInt(), 0xFF32AFC7.toInt(), 0xFF8AEAF2.toInt())
                            "Amethyst" -> intArrayOf(0xFF28153F.toInt(), 0xFF48266A.toInt(), 0xFF70429A.toInt(), 0xFFB68ADD.toInt())
                            "Glass" -> intArrayOf(0xFF467F9A.toInt(), 0xFF619BB6.toInt(), 0xFF8FC1D4.toInt(), 0xFFC5E8F2.toInt())
                            "Nether" -> intArrayOf(0xFF32141F.toInt(), 0xFF5C1C2A.toInt(), 0xFF8A2A24.toInt(), 0xFFB94B2D.toInt())
                            else -> SKY
                        }
                        sky[(y * sky.size / max(1, groundBase.toInt())).coerceIn(0, sky.lastIndex)]
                    }
                    y == ground -> when (activeTheme) {
                        "Stone" -> if (n < 60) 0xFF626262.toInt() else 0xFF9A9A9A.toInt()
                        "Diamond" -> if (n < 60) 0xFF087D91.toInt() else 0xFF38C5D7.toInt()
                        "Amethyst" -> if (n < 60) 0xFF633A83.toInt() else 0xFFB77BE0.toInt()
                        "Glass" -> if (n < 60) 0xFF6797A8.toInt() else 0xFFB8E3EE.toInt()
                        "Nether" -> if (n < 60) 0xFF7A241D.toInt() else 0xFFD64B2D.toInt()
                        else -> if (n < 60) GRASS_DARK else GRASS
                    }
                    y < ground + 4 -> when (activeTheme) {
                        "Stone" -> if (n < 40) 0xFF494949.toInt() else 0xFF777777.toInt()
                        "Diamond" -> if (n < 40) 0xFF06485A.toInt() else 0xFF087D91.toInt()
                        "Amethyst" -> if (n < 40) 0xFF352044.toInt() else 0xFF58336F.toInt()
                        "Glass" -> if (n < 40) 0xFF5A7581.toInt() else 0xFF789EAD.toInt()
                        "Nether" -> if (n < 40) 0xFF39191B.toInt() else 0xFF612522.toInt()
                        else -> if (n < 40) DIRT_DARK else DIRT
                    }
                    else -> when {
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
