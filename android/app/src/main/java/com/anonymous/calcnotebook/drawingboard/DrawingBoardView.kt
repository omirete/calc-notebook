package com.anonymous.calcnotebook.drawingboard

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import kotlin.math.hypot

class DrawingBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    // PUBLIC PROPERTIES EXPOSED TO JS ↓↓↓
    var toolMode: ToolMode = ToolMode.DRAW
    var thicknessFactor: Float = 1f
    var strokeColor: Int = Color.BLACK
    var boardColor: Int = Color.WHITE

    // Rendering optimization
    private val renderThread = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var surfaceReady = false
    private val pathCache = HashMap<Stroke, Path>()
    
    // Bitmap cache for completed strokes
    private var cachedBitmap: Bitmap? = null
    private var cachedCanvas: Canvas? = null
    private var needsFullRedraw = true

    // Eraser optimization
    private val eraseOperationQueue = mutableListOf<Pair<Float, Float>>()
    private val eraseDebouncer = Handler(Looper.getMainLooper())
    private val eraseRunnable = Runnable { processEraseQueue() }
    
    fun updateBoardColor(color: Int) {
        boardColor = color
        needsFullRedraw = true
        requestRender()
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        setZOrderOnTop(false)
        // Enable hardware acceleration if available
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /*  Internal state  */
    private val strokes = mutableListOf<Stroke>()
    private var currentStroke: Stroke? = null
    private var currentErasePath = Path()
    private val eraserPaint = Paint().apply {
        color = Color.TRANSPARENT
        strokeWidth = 30f  // Increased eraser size for better usability
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    // ───────────────────────── Surface callbacks ──────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        holder.setFormat(PixelFormat.TRANSLUCENT)
        surfaceReady = true
        needsFullRedraw = true
        requestRender()
    }
    
    override fun surfaceChanged(h: SurfaceHolder, f: Int, width: Int, height: Int) {
        // Recreate cached bitmap with new dimensions
        synchronized(this) {
            if (width > 0 && height > 0) {
                recycleCache()
                cachedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                cachedCanvas = Canvas(cachedBitmap!!)
                needsFullRedraw = true
                requestRender()
            }
        }
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        recycleCache()
    }
    
    private fun recycleCache() {
        synchronized(this) {
            cachedCanvas = null
            cachedBitmap?.recycle()
            cachedBitmap = null
        }
    }

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
                // Process last point only for smoother performance
                // Only process historical points if we're not under heavy load
                if (ev.historySize > 0 && ev.historySize < 10) {
                    // Process a subset of historical points for balance between smoothness and performance
                    val step = maxOf(1, ev.historySize / 3)
                    var i = 0
                    while (i < ev.historySize) {
                        pointerMove(ev.getHistoricalX(i), ev.getHistoricalY(i), ev.getHistoricalPressure(i))
                        i += step
                    }
                }
                
                // Always process the latest point
                pointerMove(x, y, p)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointerUp()
            }
        }
        return true
    }

    private fun pointerDown(x: Float, y: Float, pressure: Float) {
        when (toolMode) {
            ToolMode.DRAW -> {
                currentStroke = Stroke(strokeColor, thicknessFactor).also {
                    it.points.add(StrokePoint(x, y, pressure))
                    pathCache[it] = Path().apply { moveTo(x, y) }
                }
                requestRender()
            }
            ToolMode.ERASE -> {
                currentErasePath.reset()
                currentErasePath.moveTo(x, y)
                queueEraseOperation(x, y)
            }
            ToolMode.SELECT -> { /* demo omits selection for brevity */ }
        }
    }

    private fun pointerMove(x: Float, y: Float, pressure: Float) {
        when (toolMode) {
            ToolMode.DRAW -> currentStroke?.let {
                it.points.add(StrokePoint(x, y, pressure))
                pathCache[it]?.lineTo(x, y)
                requestRender()
            }
            ToolMode.ERASE -> {
                currentErasePath.lineTo(x, y)
                queueEraseOperation(x, y)
            }
            ToolMode.SELECT -> Unit
        }
    }

    private fun pointerUp() {
        when (toolMode) {
            ToolMode.DRAW -> currentStroke?.let {
                strokes.add(it)
                
                // Once the stroke is complete, render it to the cached bitmap
                synchronized(this) {
                    cachedCanvas?.let { canvas ->
                        if (pathCache.containsKey(it)) {
                            it.paint.color = it.color
                            it.paint.strokeWidth = it.widthFor(it.points.last())
                            canvas.drawPath(pathCache[it]!!, it.paint)
                        }
                    }
                }
                
                currentStroke = null
                requestRender()
            }
            ToolMode.ERASE -> {
                processEraseQueue() // Process any remaining erase operations
                currentErasePath.reset()
            }
            ToolMode.SELECT -> Unit
        }
    }

    // ───────────────────────── Eraser optimization ────────────────────────────────

    private fun queueEraseOperation(x: Float, y: Float) {
        synchronized(eraseOperationQueue) {
            eraseOperationQueue.add(Pair(x, y))
        }
        
        // Debounce erase operations to reduce processing load
        eraseDebouncer.removeCallbacks(eraseRunnable)
        eraseDebouncer.postDelayed(eraseRunnable, 16) // ~60fps
    }
    
    private fun processEraseQueue() {
        val points = synchronized(eraseOperationQueue) {
            val pts = eraseOperationQueue.toList()
            eraseOperationQueue.clear()
            pts
        }
        
        if (points.isEmpty()) return
        
        val eraserRadius = eraserPaint.strokeWidth / 2f
        val toRemove = mutableListOf<Stroke>()
        
        // Build a spatial index for more efficient point lookup
        for (stroke in strokes) {
            for (point in points) {
                val (x, y) = point
                if (stroke.points.any { p ->
                    hypot((p.x + stroke.translation.x) - x, (p.y + stroke.translation.y) - y) < eraserRadius
                }) {
                    toRemove.add(stroke)
                    break // No need to check more points for this stroke
                }
            }
        }
        
        if (toRemove.isNotEmpty()) {
            strokes.removeAll(toRemove)
            toRemove.forEach { pathCache.remove(it) }
            needsFullRedraw = true
            requestRender()
        }
    }

    // ───────────────────────── Rendering ─────────────────────────────────────

    private var renderRequested = false
    
    private fun requestRender() {
        if (!surfaceReady || renderRequested) return
        renderRequested = true
        
        renderThread.execute {
            renderFrame()
            mainHandler.post { renderRequested = false }
        }
    }
    
    private fun renderFrame() {
        if (!surfaceReady) return
        
        val canvas = holder.lockCanvas() ?: return
        try {
            // Full redraw if needed
            if (needsFullRedraw) {
                // Redraw the background and all strokes to the cached bitmap
                synchronized(this) {
                    cachedCanvas?.let { c ->
                        c.drawColor(boardColor, PorterDuff.Mode.SRC)
                        
                        for (stroke in strokes) {
                            if (!pathCache.containsKey(stroke)) {
                                // Regenerate path if not in cache
                                val path = Path()
                                stroke.points.forEachIndexed { idx, p ->
                                    val px = p.x + stroke.translation.x
                                    val py = p.y + stroke.translation.y
                                    if (idx == 0) path.moveTo(px, py)
                                    else path.lineTo(px, py)
                                }
                                pathCache[stroke] = path
                            }
                            
                            stroke.paint.color = stroke.color
                            stroke.paint.strokeWidth = stroke.widthFor(stroke.points.last())
                            c.drawPath(pathCache[stroke]!!, stroke.paint)
                        }
                    }
                }
                needsFullRedraw = false
            }
            
            // Draw the cached bitmap to the surface
            synchronized(this) {
                cachedBitmap?.let { 
                    canvas.drawColor(boardColor, PorterDuff.Mode.SRC)
                    canvas.drawBitmap(it, 0f, 0f, null)
                }
            }
            
            // Draw the current stroke if exists
            currentStroke?.let { s ->
                val path = pathCache[s] ?: Path()
                s.paint.color = s.color
                s.paint.strokeWidth = s.widthFor(s.points.last())
                canvas.drawPath(path, s.paint)
            }
            
            // Draw eraser overlay if active
            if (toolMode == ToolMode.ERASE && !currentErasePath.isEmpty) {
                canvas.drawPath(currentErasePath, eraserPaint)
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }
}