package com.anonymous.calcnotebook.drawingboard

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class DrawingBoardPackage : ReactPackage {
  override fun createViewManagers(
      reactContext: ReactApplicationContext
  ): MutableList<ViewManager<*, *>> = mutableListOf(DrawingBoardManager(reactContext))

  override fun createNativeModules(reactContext: ReactApplicationContext) =
      emptyList<com.facebook.react.bridge.NativeModule>()
}
