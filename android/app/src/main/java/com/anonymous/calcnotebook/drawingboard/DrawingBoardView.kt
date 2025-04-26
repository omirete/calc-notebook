package com.anonymous.calcnotebook.drawingboard

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class DrawingBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    // PUBLIC PROPERTIES EXPOSED TO JS ↓↓↓
    var toolMode: ToolMode = ToolMode.DRAW
    var thicknessFactor: Float = 1f
    var strokeColor: Int = Color.BLACK

    /** Background colour of the board (defaults to white). */
    var boardColor: Int = Color.WHITE

    fun updateBoardColor(color: Int) {
        boardColor = color
        dirty = true
        renderSurface()
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        setZOrderOnTop(false)   // we'll blit directly to a Surface
    }

    /*  Internal state  */
    private val strokes = mutableListOf<Stroke>()
    private var currentStroke: Stroke? = null
    private var currentErasePath = Path()
    private val eraserPaint = Paint().apply {
        color = Color.TRANSPARENT
        strokeWidth = 10f
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    /** Thread-safe flag so we draw only when needed */
    @Volatile private var dirty = true

    // ───────────────────────── Surface callbacks ──────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        holder.setFormat(PixelFormat.TRANSLUCENT)
        renderSurface()
    }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hgt: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    // ───────────────────────── Touch / pen handling ──────────────────────────

    override fun onTouchEvent(ev: MotionEvent): Boolean {
    val x = ev.x
    val y = ev.y
    val p = ev.pressure

    when (ev.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            pointerDown(x, y, p)
        }
        MotionEvent.ACTION_MOVE -> {
            for (i in 0 until ev.historySize) {
                pointerMove(ev.getHistoricalX(i), ev.getHistoricalY(i), ev.getHistoricalPressure(i))
            }
            pointerMove(x, y, p)
        }
        MotionEvent.ACTION_UP -> {
            pointerUp()
        }
        MotionEvent.ACTION_CANCEL -> {
            pointerUp()
        }
        else -> {
            // Unhandled touch event
        }
    }
    return true
}

    private fun pointerDown(x: Float, y: Float, pressure: Float) {
        when (toolMode) {
            ToolMode.DRAW -> {
                currentStroke = Stroke(strokeColor, thicknessFactor).also {
                    it.points += StrokePoint(x, y, pressure)
                }
            }
            ToolMode.ERASE -> {
                currentErasePath.reset()
                currentErasePath.moveTo(x, y)
                eraseAt(x, y)
            }
            ToolMode.SELECT -> { /* demo omits selection for brevity */ }
        }
    }

    private fun pointerMove(x: Float, y: Float, pressure: Float) {
        when (toolMode) {
            ToolMode.DRAW -> currentStroke?.let {
                it.points += StrokePoint(x, y, pressure)
                dirty = true
                renderSurface()
            }
            ToolMode.ERASE -> {
                currentErasePath.lineTo(x, y)
                eraseAt(x, y)
            }
            ToolMode.SELECT -> Unit
        }
    }

    private fun pointerUp() {
        when (toolMode) {
            ToolMode.DRAW -> currentStroke?.let {
                strokes += it
                currentStroke = null
                dirty = true
                renderSurface()
            }
            ToolMode.ERASE   -> Unit
            ToolMode.SELECT  -> Unit
        }
    }

    // ───────────────────────── Eraser helpers ────────────────────────────────

    private fun eraseAt(x: Float, y: Float) {
        val threshold = eraserPaint.strokeWidth
        val toRemove = strokes.filter { s ->
            s.points.any { p ->
                hypot((p.x + s.translation.x) - x, (p.y + s.translation.y) - y) < threshold
            }
        }
        if (toRemove.isNotEmpty()) {
            strokes.removeAll(toRemove)
            dirty = true
            renderSurface()
        }
    }

    // ───────────────────────── Rendering ─────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        // SurfaceView’s onDraw is called only with hardware accel OFF;
        // we draw onto the Surface directly in renderSurface() below.
    }

    private fun renderSurface() {
        if (!dirty) return
        dirty = false
        val c = holder.lockCanvas() ?: return
        try {
            c.drawColor(boardColor, PorterDuff.Mode.SRC)
            // completed strokes
            strokes.forEach { stroke ->
                stroke.paint.strokeWidth = stroke.widthFor(stroke.points.last())
                c.drawPath(stroke.toPath(), stroke.paint)
            }
            // currently drawing stroke
            currentStroke?.let { s ->
                s.paint.strokeWidth = s.widthFor(s.points.last())
                c.drawPath(s.toPath(), s.paint)
            }
            // eraser overlay
            if (toolMode == ToolMode.ERASE && !currentErasePath.isEmpty) {
                c.drawPath(currentErasePath, eraserPaint)
            }
        } finally {
            holder.unlockCanvasAndPost(c)
        }
    }
}
