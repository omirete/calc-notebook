import React, { FC, useCallback, useEffect, useState } from "react";
import { Dimensions, StyleSheet } from "react-native";
import { GestureDetector, Gesture } from "react-native-gesture-handler";
import { Canvas, Path, Skia, SkPath, Text, useFont } from "@shopify/react-native-skia";
import { runOnJS, useDerivedValue, useSharedValue } from "react-native-reanimated";
import { Point, Tool } from "@/types/sketch";

interface SketchCanvasProps {
  tool: Tool;
}

const SketchCanvas: FC<SketchCanvasProps> = ({ tool }) => {
  const { width, height } = Dimensions.get("window");

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

  const [myPaths, setMyPaths] = useState<SkPath[]>([])

  const gesture = Gesture.Pan()
    .onBegin(({ x, y, stylusData }) => {
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
      myStylusData.value = stylusData;
      livePath.value.lineTo(x, y);
      livePoints.value.push({
        x: x,
        y: y,
        pressure: stylusData?.pressure ?? 1
      });
    })
    .onEnd(() => {
      const svgString = livePath.value.toSVGString()
      const newPath = Skia.Path.MakeFromSVGString(svgString);
      if (newPath) {
        runOnJS(appendPath)(newPath);
      }
    });

  // derived Skia string
  const displayText = useDerivedValue(() =>
    myStylusData.value ? JSON.stringify(myStylusData.value, null, 2) : 'test'
  );

  return (
    <GestureDetector gesture={gesture}>
      <Canvas style={[styles.canvas, { width, height }]}>
        {font && (
          <Text
            x={20}
            y={40}
            text={displayText ?? 'Loading...'}
            font={font}
            color="black"
          />
        )}
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
