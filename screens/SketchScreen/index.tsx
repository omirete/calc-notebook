import React, { useState } from "react";
import { View, StyleSheet } from "react-native";
import Toolbar from "@/components/Toolbar";
import SketchCanvas from "@/components/SketchCanvas";
import { SketchDocument, Tool } from "@/types/sketch";

export default function SketchScreen(): JSX.Element {
  const [tool, setTool] = useState<Tool>("pencil");
  const [doc, setDoc] = useState<SketchDocument>(() => ({
    metadata: {
      name: "Untitled",
      author: "Me",
      size: "A4",
      orientation: "Portrait",
      units: "mm",
      skcVersion: "0.0.1",
    },
    pages: [
      {
        number: 1,
        data: {},
        shapes: {},
        paths: [],
      },
    ],
  }));


  return (
    <View style={styles.container}>
      <Toolbar activeTool={tool} onSelectTool={setTool} />
      {/* <SketchCanvas tool={tool} document={doc} onUpdateDocument={setDoc} /> */}
      <SketchCanvas />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});
