package com.anonymous.calcnotebook.drawingboard

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.*
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.*

@ReactModule(name = DrawingBoardManager.NAME)
class DrawingBoardManager(private val ctx: ReactApplicationContext) :
    SimpleViewManager<DrawingBoardView>() {

  companion object {
    const val NAME = "RNDrawingBoard"
  }

  override fun getName() = NAME

  override fun createViewInstance(reactCtx: ThemedReactContext): DrawingBoardView =
      DrawingBoardView(reactCtx)

  // ───────── Exposed props ────────────────────────────────────────────────

  @ReactProp(name = "tool")
  fun setTool(view: DrawingBoardView, tool: String) = run {
    view.toolMode = ToolMode.valueOf(tool.uppercase())
  }

  @ReactProp(name = "thickness", defaultFloat = 1f)
  fun setThickness(view: DrawingBoardView, t: Float) {
    view.thicknessFactor = t
  }

  @ReactProp(name = "color", customType = "Color", defaultInt = 0xff000000.toInt())
  fun setColor(view: DrawingBoardView, c: Int) {
    view.strokeColor = c
  }

  @ReactProp(name = "boardColor", customType = "Color", defaultInt = 0xFFFFFFFF.toInt())
  fun setBoardColor(view: DrawingBoardView, color: Int) {
    view.updateBoardColor(color)
  }

  @ReactProp(name = "predictionMultiplier", defaultFloat = 0f)
  fun setPredictionMultiplier(view: DrawingBoardView, multiplier: Float) {
    view.predictionMultiplier = multiplier
  }

  @ReactProp(name = "predictedStrokeColor", customType = "Color")
  fun setPredictedStrokeColor(view: DrawingBoardView, color: Int?) {
    view.predictedStrokeColor = color
  }

  @ReactProp(name = "predictAfterNPoints", defaultInt = 30)
  fun setPredictAfterNPoints(view: DrawingBoardView, n: Int) {
    view.predictAfterNPoints = n
  }
}
