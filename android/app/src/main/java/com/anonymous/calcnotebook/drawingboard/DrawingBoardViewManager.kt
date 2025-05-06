package com.anonymous.calcnotebook.drawingboard

import androidx.annotation.UiThread
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class DrawingBoardViewManager : SimpleViewManager<ComposeView>() {

  override fun getName() = "RNDrawingBoard"

  private var bgColor = Color.Black
  private var strokeColor = Color.White
  private var strokeSize = 3f

  override fun createViewInstance(reactContext: ThemedReactContext): ComposeView =
      ComposeView(reactContext).apply { setContent { DrawingBoardComposable() } }

  // ---------- React props ----------
  @ReactProp(name = "backgroundColor")
  fun setBackgroundColor(view: ComposeView, hex: String) {
    bgColor = Color(AndroidColor.parseColor(hex))
    view.setContent { DrawingBoardComposable() }
  }

  @ReactProp(name = "strokeColor")
  fun setStrokeColor(view: ComposeView, hex: String) {
    strokeColor = Color(AndroidColor.parseColor(hex))
    view.setContent { DrawingBoardComposable() }
  }

  @ReactProp(name = "strokeSize")
  fun setStrokeSize(view: ComposeView, size: Float) {
    strokeSize = size
    view.setContent { DrawingBoardComposable() }
  }

  // ---------- UI ----------
  @Composable
  private fun DrawingBoardComposable() {
    val context = LocalContext.current
    val inProgressView = remember { InProgressStrokesView(context) }
    val finishedStrokes = remember { mutableStateOf(emptySet<Stroke>()) }
    val strokeIds = remember { mutableMapOf<Int, InProgressStrokeId>() }

    // renderer & brush live at composition level
    val renderer = remember { CanvasStrokeRenderer.create() }
    val defaultBrush =
        remember(strokeColor, strokeSize) {
          Brush.createWithColorIntArgb(
              family = StockBrushes.pressurePenLatest,
              colorIntArgb = strokeColor.toArgb(),
              size = strokeSize,
              epsilon = 0.1f)
        }

    // finished‑stroke callback
    DisposableEffect(inProgressView) {
      val listener =
          object : InProgressStrokesFinishedListener {
            @UiThread
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
              finishedStrokes.value += strokes.values
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

                when (event.actionMasked) {
                  MotionEvent.ACTION_DOWN,
                  MotionEvent.ACTION_POINTER_DOWN -> {
                    val idx = event.actionIndex
                    val pid = event.getPointerId(idx)
                    requestUnbufferedDispatch(event)
                    strokeIds[pid] = inProgressView.startStroke(event, pid, defaultBrush)
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
            }
          })

      // render finished strokes
      Canvas(Modifier.fillMaxSize()) {
        finishedStrokes.value.forEach { stroke ->
          renderer.draw(drawContext.canvas.nativeCanvas, stroke, android.graphics.Matrix())
        }
      }
    }
  }
}
