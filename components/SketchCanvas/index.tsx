import React from "react";
import { Dimensions, StyleSheet } from "react-native";
import { GestureDetector, Gesture } from "react-native-gesture-handler";
import { Canvas, Path, Skia, Text, useFont } from "@shopify/react-native-skia";
import { useDerivedValue, useSharedValue } from "react-native-reanimated";
import { Point } from "@/types/sketch";

export default function SketchCanvas() {
  const { width, height } = Dimensions.get("window");

  const font = useFont(require("../../assets/fonts/SpaceMono-Regular.ttf"), 14); // Use your own font here
  const myStylusData = useSharedValue<any>({});
  // const livePoints = useSharedValue<Point[]>([]);
  const livePath = useSharedValue(Skia.Path.Make());

  const gesture = Gesture.Pan()
    .onStart(({ x, y, stylusData }) => {
      myStylusData.value = stylusData;
      const p = Skia.Path.Make();
      p.moveTo(x, y);
      livePath.value = p;
      // livePoints.value = [{
      //   x: x,
      //   y: y,
      //   pressure: stylusData?.pressure ?? 1
      // }];
    })
    .onChange(({ x, y, stylusData }) => {
      myStylusData.value = stylusData;
      livePath.value.lineTo(x, y);
      // livePoints.value.push({
      //   x: x,
      //   y: y,
      //   pressure: stylusData?.pressure ?? 1
      // });
    })
    .onEnd(({ stylusData }) => {
      myStylusData.value = stylusData;
      // livePoints.value = [];
    });

  // derived Skia string
  const displayText = useDerivedValue(() =>
    JSON.stringify(myStylusData.value, null, 2)
  );

  return (
    <GestureDetector gesture={gesture}>
      <Canvas style={[styles.canvas, { width, height }]}>
        {font && (
          <Text
            x={20}
            y={40}
            text={displayText}
            font={font}
            color="black"
          />
        )}
        {/* {livePoints.value.length > 0 && <Path path={""} />} */}
        {livePath.value && (
          <Path
            path={livePath}
            color="black"
            style="stroke"
            strokeWidth={2}
          />
        )}
      </Canvas>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  canvas: {
    flex: 1,
    backgroundColor: "#fafafa",
  },
});
