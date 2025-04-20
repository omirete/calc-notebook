import React, { FC, useCallback, useEffect, useRef, useState } from "react";
import { Dimensions, StyleSheet } from "react-native";
import { GestureDetector, Gesture } from "react-native-gesture-handler";
import { Canvas, Path, Skia, SkiaDomView, SkPath, useFont } from "@shopify/react-native-skia";
import { runOnJS, useSharedValue } from "react-native-reanimated";
import { Point, Tool } from "@/types/sketch";

interface SketchCanvasProps {
  tool: Tool;
}

const SketchCanvas: FC<SketchCanvasProps> = ({ tool }) => {
  const { width, height } = Dimensions.get("window");
  const canvasRef = useRef<SkiaDomView>(null);
  useEffect(() => {
    if (tool === 'clear') {
      setMyPaths([]);
      livePath.value = Skia.Path.Make();
    }
  }, [tool]);

  const font = useFont(require("../../assets/fonts/SpaceMono-Regular.ttf"), 14); // Use your own font here
  const myStylusData = useSharedValue<any>({});

  const livePoints = useSharedValue<Point[]>([]);
  const livePath = useSharedValue(Skia.Path.Make());

  const appendPath = useCallback((newPath: SkPath) => {
    setMyPaths((prev) => [...prev, newPath]);
  }, []);
  const redrawCanvas = useCallback(() => {
    canvasRef.current?.redraw();
  }, []);

  const [myPaths, setMyPaths] = useState<SkPath[]>([])

  const gesture = Gesture.Pan()
    .onBegin(({ x, y, stylusData }) => {
      if (tool !== 'pencil') return;
      if (stylusData?.pressure === undefined) return;
      myStylusData.value = stylusData;
      const p = Skia.Path.Make();
      p.moveTo(x, y);
      livePath.value = p;
      livePoints.value = [{
        x: x,
        y: y,
        pressure: stylusData?.pressure ?? 1
      }];
    })
    .onChange(({ x, y, stylusData }) => {
      if (tool !== 'pencil') return;
      if (stylusData?.pressure === undefined) return;
      myStylusData.value = stylusData;
      livePath.value.lineTo(x, y);
      livePoints.value.push({
        x: x,
        y: y,
        pressure: stylusData?.pressure ?? 1
      });
      runOnJS(redrawCanvas)();
    })
    .onEnd(({ stylusData }) => {
      if (tool !== 'pencil') return;
      if (stylusData?.pressure === undefined) return;
      const svgString = livePath.value.toSVGString()
      const newPath = Skia.Path.MakeFromSVGString(svgString);
      if (newPath) {
        runOnJS(appendPath)(newPath);
      }
    });

  return (
    <GestureDetector gesture={gesture}>
      <Canvas style={[styles.canvas, { width, height }]} ref={canvasRef}>
        {myPaths && myPaths.map((path, index) => (
          <Path
            key={index}
            path={path}
            color="white"
            style="stroke"
            strokeWidth={2}
          />
        ))}
        <Path
          path={livePath}
          color="white"
          style="stroke"
          strokeWidth={2}
        />
      </Canvas>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  canvas: {
    flex: 1,
    backgroundColor: "#0a1e3b",
  },
});

export default SketchCanvas;
