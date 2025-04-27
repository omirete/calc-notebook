package com.anonymous.calcnotebook.drawingboard

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.*
import java.util.concurrent.Executors
import kotlin.math.hypot

class DrawingBoardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs), SurfaceHolder.Callback {

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

  // Eraser live variables and eraser boundary indicator
  private var currentErasePath = Path()
  private var currentEraseX: Float = 0f
  private var currentEraseY: Float = 0f
  private val eraserPaint =
      Paint().apply {
        color = Color.TRANSPARENT
        strokeWidth = 30f // Increased eraser size for better usability
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
      }
  // Paint for drawing eraser collision boundary indicator
  private val eraserBoundaryPaint =
      Paint().apply {
        color = Color.RED // change color as desired
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
      }

  // Variables for predictive stylus input
  var predictionMultiplier: Float = 3f
  var predictedStrokeColor: Int? = null
  var predictAfterNPoints: Int = 30
  private var predictedPoint: StrokePoint? = null

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
        // Only process the current point to update predictedPoint reliably
        pointerMove(x, y, p)
      }
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        pointerUp()
      }
    }
    return true
  }

  private fun pointerDown(x: Float, y: Float, pressure: Float) {
    // Clear any previous prediction
    predictedPoint = null
    when (toolMode) {
      ToolMode.DRAW -> {
        currentStroke =
            Stroke(strokeColor, thicknessFactor).also {
              it.points.add(StrokePoint(x, y, pressure))
              pathCache[it] = Path().apply { moveTo(x, y) }
            }
        requestRender()
      }
      ToolMode.ERASE -> {
        currentErasePath.reset()
        currentErasePath.moveTo(x, y)
        currentEraseX = x
        currentEraseY = y
        queueEraseOperation(x, y)
        processEraseQueue() // Process immediately for live erasing
        requestRender()
      }
      ToolMode.SELECT -> {
        /* demo omits selection for brevity */
      }
    }
  }

  private fun pointerMove(x: Float, y: Float, pressure: Float) {
    when (toolMode) {
      ToolMode.DRAW ->
          currentStroke?.let { stroke ->
            stroke.points.add(StrokePoint(x, y, pressure))
            pathCache[stroke]?.lineTo(x, y)
            // Update prediction if we have at least one prior point
            if (stroke.points.size >= predictAfterNPoints) {
              val last = stroke.points.last()
              val secondLast = stroke.points[stroke.points.size - 2]
              val dx = last.x - secondLast.x
              val dy = last.y - secondLast.y
              predictedPoint =
                  StrokePoint(
                      last.x + dx * predictionMultiplier,
                      last.y + dy * predictionMultiplier,
                      last.pressure)
            }
            requestRender()
          }
      ToolMode.ERASE -> {
        currentErasePath.lineTo(x, y)
        currentEraseX = x
        currentEraseY = y
        queueEraseOperation(x, y)
        processEraseQueue() // Process continuously during drag
        requestRender()
      }
      ToolMode.SELECT -> Unit
    }
  }

  private fun pointerUp() {
    when (toolMode) {
      ToolMode.DRAW ->
          currentStroke?.let {
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
            // Clear predicted point when stroke ends
            predictedPoint = null
            requestRender()
          }
      ToolMode.ERASE -> {
        processEraseQueue() // Process any remaining erase operations
        currentErasePath.reset()
        requestRender()
      }
      ToolMode.SELECT -> Unit
    }
  }

  // ───────────────────────── Eraser optimization ────────────────────────────────

  private fun queueEraseOperation(x: Float, y: Float) {
    synchronized(eraseOperationQueue) { eraseOperationQueue.add(Pair(x, y)) }

    // Debounce erase operations to reduce processing load
    eraseDebouncer.removeCallbacks(eraseRunnable)
    eraseDebouncer.postDelayed(eraseRunnable, 16) // ~60fps
  }

  // Add this helper function somewhere in the class (for instance, just below
  // processEraseQueue())
  private fun distancePointToSegment(
      px: Float,
      py: Float,
      x1: Float,
      y1: Float,
      x2: Float,
      y2: Float
  ): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    if (dx == 0f && dy == 0f) return hypot(px - x1, py - y1)
    val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
    return when {
      t < 0f -> hypot(px - x1, py - y1)
      t > 1f -> hypot(px - x2, py - y2)
      else -> {
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        hypot(px - projX, py - projY)
      }
    }
  }

  private fun processEraseQueue() {
    val points =
        synchronized(eraseOperationQueue) {
          val pts = eraseOperationQueue.toList()
          eraseOperationQueue.clear()
          pts
        }

    if (points.isEmpty()) return

    val eraserRadius = eraserPaint.strokeWidth / 2f
    val toRemove = mutableListOf<Stroke>()

    for (stroke in strokes) {
      var shouldRemove = false
      for ((eraserX, eraserY) in points) {
        val pts = stroke.points
        if (pts.isEmpty()) continue
        if (pts.size == 1) {
          // Single point stroke
          if (hypot(pts[0].x - eraserX, pts[0].y - eraserY) < eraserRadius) {
            shouldRemove = true
            break
          }
        } else {
          // Check each segment between consecutive points
          for (i in 0 until pts.size - 1) {
            val p1 = pts[i]
            val p2 = pts[i + 1]
            if (distancePointToSegment(eraserX, eraserY, p1.x, p1.y, p2.x, p2.y) < eraserRadius) {
              shouldRemove = true
              break
            }
          }
        }
        if (shouldRemove) break
      }
      if (shouldRemove) {
        toRemove.add(stroke)
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
    if (!surfaceReady) return
    renderThread.execute { renderFrame() }
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
                  if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
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
        // Draw predicted line if available
        predictedPoint?.let { pred ->
          val predPath =
              Path().apply {
                moveTo(s.points.last().x + s.translation.x, s.points.last().y + s.translation.y)
                lineTo(pred.x + s.translation.x, pred.y + s.translation.y)
              }
          predictedStrokeColor?.let { color ->
            val tempPaint = Paint(s.paint).apply { this.color = color }
            canvas.drawPath(predPath, tempPaint)
          } ?: canvas.drawPath(predPath, s.paint)
        }
      }

      // Draw eraser overlay if active
      if (toolMode == ToolMode.ERASE && !currentErasePath.isEmpty) {
        canvas.drawPath(currentErasePath, eraserPaint)
        // Draw collision boundary indicator circle
        canvas.drawCircle(
            currentEraseX, currentEraseY, eraserPaint.strokeWidth / 2f, eraserBoundaryPaint)
      }
    } finally {
      holder.unlockCanvasAndPost(canvas)
    }
  }
}
