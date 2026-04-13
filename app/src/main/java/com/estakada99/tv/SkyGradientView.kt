package com.estakada99.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

/**
 * Animated gradient background matching e99.live sky-motion.
 * A 135-degree 7-stop gradient shifts position over a 100-second cycle.
 */
class SkyGradientView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val CYCLE_MS = 100_000L
        private const val FRAME_MS = 83L // ~12 fps, matching e99.live MIN_VISIBLE_FRAME_MS

        private val COLORS = intArrayOf(
            0xFF56c1e8.toInt(), // sky blue
            0xFF7ed6f5.toInt(), // light blue
            0xFFa8e6cf.toInt(), // mint green
            0xFFf9e4b7.toInt(), // warm yellow
            0xFFf7b8a8.toInt(), // salmon/rose
            0xFFc4a8e8.toInt(), // lavender
            0xFF82c4f0.toInt()  // cerulean
        )

        private val POSITIONS = floatArrayOf(0f, 0.18f, 0.35f, 0.52f, 0.68f, 0.82f, 1f)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderMatrix = Matrix()
    private var shader: LinearGradient? = null
    private var lastW = 0
    private var lastH = 0

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            invalidate()
            if (running) handler.postDelayed(this, FRAME_MS)
        }
    }

    /** Compute background position exactly like e99.live sky-motion.js bgXY() */
    private fun bgXY(ms: Long): Pair<Float, Float> {
        val t = (ms % CYCLE_MS).toFloat() / CYCLE_MS
        val x: Float
        val y: Float
        if (t < 0.5f) {
            val u = t * 2f
            x = u // 0..1
            y = 0.25f + u * 0.5f // 0.25..0.75
        } else {
            val v = (t - 0.5f) * 2f
            x = 1f - v // 1..0
            y = 0.75f - v * 0.5f // 0.75..0.25
        }
        return Pair(x, y)
    }

    private fun ensureShader(w: Int, h: Int) {
        if (w == lastW && h == lastH && shader != null) return
        lastW = w
        lastH = h

        // The gradient runs at 135deg across a 4x oversized area (matching 400% 400% CSS)
        val size = maxOf(w, h) * 4f

        // 135deg gradient: top-left to bottom-right
        shader = LinearGradient(
            0f, 0f,
            size * 0.7071f, size * 0.7071f, // cos(45), sin(45) for 135deg
            COLORS,
            POSITIONS,
            Shader.TileMode.MIRROR
        )
        paint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w == 0 || h == 0) return

        ensureShader(w, h)

        val (px, py) = bgXY(System.currentTimeMillis())

        // Translate the oversized gradient to simulate CSS background-position
        val size = maxOf(w, h) * 4f
        val offsetX = -px * (size - w)
        val offsetY = -py * (size - h)

        shaderMatrix.reset()
        shaderMatrix.postTranslate(offsetX, offsetY)
        shader?.setLocalMatrix(shaderMatrix)

        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    fun startAnimation() {
        if (running) return
        running = true
        handler.post(tickRunnable)
    }

    fun stopAnimation() {
        running = false
        handler.removeCallbacks(tickRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }
}
