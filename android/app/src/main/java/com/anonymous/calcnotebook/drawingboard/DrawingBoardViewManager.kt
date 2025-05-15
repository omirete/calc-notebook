package com.anonymous.calcnotebook.drawingboard

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.annotation.UiThread
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.geometry.ImmutableBox
import androidx.ink.geometry.ImmutableVec
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class DrawingBoardViewManager : SimpleViewManager<ComposeView>() {

  override fun getName() = "RNDrawingBoard"

  // Create state holders
  private val bgColorState = mutableStateOf(Color.Black)
  private val strokeColorState = mutableStateOf(Color.White)
  private val strokeSizeState = mutableStateOf(3f)
  private val finishedStrokes = mutableStateOf(listOf<Stroke>()) // Use List for reliable updates
  private val toolState = mutableStateOf("draw") // New tool state: "draw" or "erase"

  // Track eraser position for visualization
  private val eraserPositionState = mutableStateOf<Pair<Float, Float>?>(null)
  private val eraserActiveState = mutableStateOf(false)

  // Track eraser path for more effective erasing
  private val eraserPathPoints = mutableStateOf<List<Pair<Float, Float>>>(emptyList())

  // Selection state
  private val selectionBoxState = mutableStateOf<Pair<Offset, Offset>?>(null) // (start, end)
  private val isSelectingState = mutableStateOf(false)
  private val selectedStrokeIndicesState =
      mutableStateOf<Set<Int>>(emptySet()) // Use indices for selection
  private val selectionBoundsState =
      mutableStateOf<Pair<Offset, Offset>?>(null) // (topLeft, bottomRight)

  // Single shared ComposeView instance to maintain state
  private var currentComposeView: ComposeView? = null

  override fun createViewInstance(reactContext: ThemedReactContext): ComposeView =
      ComposeView(reactContext).apply {
        currentComposeView = this
        setContent { DrawingBoardComposable() }
      }

  // ---------- React props ----------
  @ReactProp(name = "backgroundColor")
  fun setBackgroundColor(view: ComposeView, hex: String) {
    try {
      bgColorState.value = Color(AndroidColor.parseColor(hex))
    } catch (e: Exception) {
      // Silent catch for invalid colors
    }
  }

  @ReactProp(name = "strokeColor")
  fun setStrokeColor(view: ComposeView, hex: String) {
    try {
      val newColor = Color(AndroidColor.parseColor(hex))
      strokeColorState.value = newColor
      if (toolState.value == "select" && selectedStrokeIndicesState.value.isNotEmpty()) {
        // Change color of selected strokes by index
        val updatedStrokes =
            finishedStrokes.value.mapIndexed { idx, stroke ->
              if (selectedStrokeIndicesState.value.contains(idx)) {
                val newBrush =
                    Brush.createWithColorIntArgb(
                        family = StockBrushes.pressurePenLatest,
                        colorIntArgb = newColor.toArgb(),
                        size = stroke.brush.size,
                        epsilon = 0.1f)
                stroke.copy(newBrush)
              } else stroke
            }
        finishedStrokes.value = updatedStrokes
        // selectedStrokeIndicesState.value = emptySet()
        // selectionBoundsState.value = null
      }
    } catch (e: Exception) {
      // Silent catch for invalid colors
    }
  }

  @ReactProp(name = "strokeSize")
  fun setStrokeSize(view: ComposeView, size: Float) {
    strokeSizeState.value = size
    if (toolState.value == "select" && selectedStrokeIndicesState.value.isNotEmpty()) {
      val updatedStrokes =
          finishedStrokes.value.mapIndexed { idx, stroke ->
            if (selectedStrokeIndicesState.value.contains(idx)) {
              val newBrush =
                  Brush.createWithColorIntArgb(
                      family = StockBrushes.pressurePenLatest,
                      colorIntArgb = stroke.brush.colorIntArgb,
                      size = size,
                      epsilon = 0.1f)
              stroke.copy(newBrush)
            } else stroke
          }
      finishedStrokes.value = updatedStrokes
      // selectedStrokeIndicesState.value = emptySet()
      // selectionBoundsState.value = null
    }
  }

  @ReactProp(name = "tool")
  fun setTool(view: ComposeView, tool: String) {
    toolState.value = tool

    // Special case: if switching to erase tool and there are selected strokes, remove them
    if (tool == "erase" && selectedStrokeIndicesState.value.isNotEmpty()) {
      // Remove the selected strokes
      val selectedIndices = selectedStrokeIndicesState.value
      finishedStrokes.value =
          finishedStrokes.value.filterIndexed { idx, _ -> !selectedIndices.contains(idx) }
    }

    // Reset selection state when switching away from select tool
    if (tool != "select") {
      selectionBoxState.value = null
      isSelectingState.value = false
      selectedStrokeIndicesState.value = emptySet()
      selectionBoundsState.value = null
    }
  }

  // ---------- UI ----------
  @Composable
  private fun DrawingBoardComposable() {
    // Access state directly
    val bgColor by bgColorState
    val strokeColor by strokeColorState
    val strokeSize by strokeSizeState
    val currentStrokes by finishedStrokes
    val tool by toolState
    val eraserPosition by eraserPositionState
    val eraserActive by eraserActiveState
    val eraserPath by eraserPathPoints
    val selectionBox by selectionBoxState
    val isSelecting by isSelectingState
    val selectedStrokeIndices by selectedStrokeIndicesState
    val selectionBounds by selectionBoundsState

    val context = LocalContext.current
    val inProgressView = remember { InProgressStrokesView(context) }
    val strokeIds = remember { mutableMapOf<Int, InProgressStrokeId>() }

    // renderer & brush live at composition level
    val renderer = remember { CanvasStrokeRenderer.create() }

    // Create a new brush on each recomposition if strokeColor or strokeSize has changed
    val defaultBrush =
        Brush.createWithColorIntArgb(
            family = StockBrushes.pressurePenLatest,
            colorIntArgb = strokeColor.toArgb(),
            size = strokeSize,
            epsilon = 0.1f)

    // Coroutine scope for eraser operations
    val scope = rememberCoroutineScope()

    // Eraser function to remove strokes
    fun eraseWholeStrokes(eraserCenter: ImmutableVec, eraserRadius: Float) {
      // Use a much more aggressive threshold for detection
      val threshold = 0.001f

      // Create a bounding box for the eraser circle
      val left = eraserCenter.x - eraserRadius
      val top = eraserCenter.y - eraserRadius
      val right = eraserCenter.x + eraserRadius
      val bottom = eraserCenter.y + eraserRadius

      // Create a box for initial quick intersection check
      val eraserBox =
          ImmutableBox.fromTwoPoints(ImmutableVec(left, top), ImmutableVec(right, bottom))

      // Improved: Two-step eraser detection (box for quick reject, then circle for accuracy)
      val eraserRadiusSquared = eraserRadius * eraserRadius
      val strokesToErase =
          finishedStrokes.value.filter { stroke ->
            // Step 1: Quick box-based rejection test
            if (!stroke.shape.computeCoverageIsGreaterThan(eraserBox, 0.001f)) {
              return@filter false
            }
            // Step 2: Accurate circular hit test using outline vertices
            val mesh = stroke.shape
            val groupCount = mesh.getRenderGroupCount()
            val hit =
                (0 until groupCount).any { groupIdx ->
                  val outlineCount = mesh.getOutlineCount(groupIdx)
                  (0 until outlineCount).any { outlineIdx ->
                    val vertexCount = mesh.getOutlineVertexCount(groupIdx, outlineIdx)
                    val outPos = androidx.ink.geometry.MutableVec()
                    (0 until vertexCount).any { vertexIdx ->
                      val pos =
                          mesh.populateOutlinePosition(groupIdx, outlineIdx, vertexIdx, outPos)
                      val dx = pos.x - eraserCenter.x
                      val dy = pos.y - eraserCenter.y
                      val distSq = dx * dx + dy * dy
                      distSq <= eraserRadiusSquared
                    }
                  }
                }
            hit
          }

      if (strokesToErase.isNotEmpty()) {
        // Update the strokes collection without the erased strokes
        finishedStrokes.value = finishedStrokes.value - strokesToErase
      }
    }

    // finished‑stroke callback
    DisposableEffect(inProgressView) {
      val listener =
          object : InProgressStrokesFinishedListener {
            @UiThread
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
              // Only add the finished strokes if we're in draw mode
              if (tool == "draw") {
                finishedStrokes.value = finishedStrokes.value + strokes.values
              }
              inProgressView.removeFinishedStrokes(strokes.keys)
            }
          }
      inProgressView.addFinishedStrokesListener(listener)
      onDispose { inProgressView.removeFinishedStrokesListener(listener) }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
      AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { ctx ->
            FrameLayout(ctx).apply {
              addView(inProgressView)
              val predictor = MotionEventPredictor.newInstance(this)

              setOnTouchListener { _, event ->
                predictor.record(event)
                val predicted = predictor.predict()

                when (tool) {
                  "draw" -> {
                    // Drawing behavior
                    when (event.actionMasked) {
                      MotionEvent.ACTION_DOWN,
                      MotionEvent.ACTION_POINTER_DOWN -> {
                        val idx = event.actionIndex
                        val pid = event.getPointerId(idx)
                        requestUnbufferedDispatch(event)

                        // Always create a fresh brush with current color and size when a new stroke
                        // starts
                        val brush =
                            Brush.createWithColorIntArgb(
                                family = StockBrushes.pressurePenLatest,
                                colorIntArgb = strokeColor.toArgb(),
                                size = strokeSize,
                                epsilon = 0.1f)

                        strokeIds[pid] = inProgressView.startStroke(event, pid, brush)
                        true
                      }
                      MotionEvent.ACTION_MOVE -> {
                        for (i in 0 until event.pointerCount) {
                          val pid = event.getPointerId(i)
                          strokeIds[pid]?.let { sid ->
                            inProgressView.addToStroke(event, pid, sid, predicted)
                          }
                        }
                        true
                      }
                      MotionEvent.ACTION_UP,
                      MotionEvent.ACTION_POINTER_UP -> {
                        val idx = event.actionIndex
                        val pid = event.getPointerId(idx)
                        strokeIds.remove(pid)?.let { sid ->
                          inProgressView.finishStroke(event, pid, sid)
                        }
                        performClick()
                        true
                      }
                      MotionEvent.ACTION_CANCEL -> {
                        strokeIds.forEach { (_, sid) -> inProgressView.cancelStroke(sid, event) }
                        strokeIds.clear()
                        true
                      }
                      else -> false
                    }
                  }
                  "erase" -> {
                    // Eraser behavior
                    when (event.actionMasked) {
                      MotionEvent.ACTION_DOWN,
                      MotionEvent.ACTION_POINTER_DOWN,
                      MotionEvent.ACTION_MOVE -> {
                        // Create circular eraser around touch point
                        val eraserRadius = strokeSize * 10
                        val x = event.x
                        val y = event.y

                        // Create center point for the circle
                        val center = ImmutableVec(x, y)

                        // Call eraser function with circle parameters
                        eraseWholeStrokes(center, eraserRadius)

                        // Log current number of strokes - helpful for debugging
                        System.out.println(
                            "Current number of strokes: ${finishedStrokes.value.size}")

                        // Update eraser position state
                        eraserPositionState.value = Pair(x, y)
                        eraserActiveState.value = true

                        // Track eraser path for enhanced erasing
                        val pathPoints = eraserPathPoints.value.toMutableList()
                        pathPoints.add(Pair(x, y))
                        // Limit path points to avoid memory issues
                        if (pathPoints.size > 100) {
                          pathPoints.removeAt(0)
                        }
                        eraserPathPoints.value = pathPoints

                        true
                      }
                      MotionEvent.ACTION_UP,
                      MotionEvent.ACTION_POINTER_UP -> {
                        // Deactivate eraser on touch up
                        eraserActiveState.value = false

                        // Clear eraser path here if you don't want to persist the path
                        eraserPathPoints.value = emptyList()

                        true
                      }
                      else -> true
                    }
                  }
                  "select" -> {
                    when (event.actionMasked) {
                      MotionEvent.ACTION_DOWN,
                      MotionEvent.ACTION_POINTER_DOWN -> {
                        val x = event.x
                        val y = event.y
                        selectionBoxState.value = Pair(Offset(x, y), Offset(x, y))
                        isSelectingState.value = true
                        true
                      }
                      MotionEvent.ACTION_MOVE -> {
                        if (isSelectingState.value) {
                          val x = event.x
                          val y = event.y
                          val start = selectionBoxState.value?.first ?: Offset(x, y)
                          selectionBoxState.value = Pair(start, Offset(x, y))
                        }
                        true
                      }
                      MotionEvent.ACTION_UP,
                      MotionEvent.ACTION_POINTER_UP -> {
                        if (isSelectingState.value) {
                          val box = selectionBoxState.value
                          if (box != null) {
                            val (start, end) = box
                            val left = minOf(start.x, end.x)
                            val right = maxOf(start.x, end.x)
                            val top = minOf(start.y, end.y)
                            val bottom = maxOf(start.y, end.y)
                            val boxRect =
                                ImmutableBox.fromTwoPoints(
                                    ImmutableVec(left, top), ImmutableVec(right, bottom))
                            // Select strokes intersecting the box
                            val indices =
                                finishedStrokes.value
                                    .mapIndexedNotNull { idx, stroke ->
                                      if (stroke.shape.computeCoverageIsGreaterThan(
                                          boxRect, 0.001f))
                                          idx
                                      else null
                                    }
                                    .toSet()
                            selectedStrokeIndicesState.value = indices
                            // Compute tight bounds with margin (same as before, but use indices)
                            if (indices.isNotEmpty()) {
                              var minX = Float.POSITIVE_INFINITY
                              var minY = Float.POSITIVE_INFINITY
                              var maxX = Float.NEGATIVE_INFINITY
                              var maxY = Float.NEGATIVE_INFINITY
                              indices.forEach { idx ->
                                val stroke = finishedStrokes.value[idx]
                                val mesh = stroke.shape
                                val groupCount = mesh.getRenderGroupCount()
                                for (g in 0 until groupCount) {
                                  val outlineCount = mesh.getOutlineCount(g)
                                  for (o in 0 until outlineCount) {
                                    val vertexCount = mesh.getOutlineVertexCount(g, o)
                                    val outPos = androidx.ink.geometry.MutableVec()
                                    for (v in 0 until vertexCount) {
                                      val pos = mesh.populateOutlinePosition(g, o, v, outPos)
                                      minX = minOf(minX, pos.x)
                                      minY = minOf(minY, pos.y)
                                      maxX = maxOf(maxX, pos.x)
                                      maxY = maxOf(maxY, pos.y)
                                    }
                                  }
                                }
                              }
                              val margin = 16f // px
                              selectionBoundsState.value =
                                  Pair(
                                      Offset(minX - margin, minY - margin),
                                      Offset(maxX + margin, maxY + margin))
                            } else {
                              selectionBoundsState.value = null
                            }
                          }
                          // Hide manual box
                          selectionBoxState.value = null
                          isSelectingState.value = false
                        }
                        true
                      }
                      else -> true
                    }
                  }
                  else -> false
                }
              }
            }
          },
          update = { frame -> frame.invalidate() })

      // render finished strokes
      Canvas(Modifier.fillMaxSize()) {
        currentStrokes.forEach { stroke ->
          renderer.draw(drawContext.canvas.nativeCanvas, stroke, android.graphics.Matrix())
        }

        // Render eraser position indicator if eraser is active
        if (eraserActive) {
          eraserPosition?.let { (x, y) ->
            val eraserSize =
                strokeSize * 10 // Changed from 6x to 10x to match actual eraser collision size
            drawCircle(
                Color.Red,
                radius = eraserSize,
                center = Offset(x, y),
                style = ComposeStroke(width = 2f))
          }
        }
      }

      // Render selection box (manual, dashed)
      val selectionBox = selectionBoxState.value
      if (tool == "select" && selectionBox != null) {
        val (start, end) = selectionBox
        Canvas(Modifier.fillMaxSize()) {
          drawRect(
              color = Color.Blue,
              topLeft = start,
              size = androidx.compose.ui.geometry.Size(end.x - start.x, end.y - start.y),
              style =
                  ComposeStroke(
                      width = 2f,
                      pathEffect =
                          androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                              floatArrayOf(12f, 12f))))
        }
      }
      // Render selection bounds (tight, solid)
      val selectionBounds = selectionBoundsState.value
      if (tool == "select" && selectionBounds != null) {
        val (topLeft, bottomRight) = selectionBounds
        Canvas(Modifier.fillMaxSize()) {
          drawRect(
              color = Color.Blue,
              topLeft = topLeft,
              size =
                  androidx.compose.ui.geometry.Size(
                      bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
              style =
                  ComposeStroke(
                      width = 2f,
                      pathEffect =
                          androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                              floatArrayOf(8f, 8f))))
        }
      }
    }
  }
}
