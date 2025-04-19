import React from "react";
import { View, TouchableOpacity, Text, StyleSheet } from "react-native";
import { Tool } from "@/types/sketch";

interface Props {
    activeTool: Tool;
    onSelectTool: (tool: Tool) => void;
}

const tools: Tool[] = [
    "pencil",
    "line",
    "rectangle",
    "ellipse",
    "text",
    "select",
    "move",
    "scale",
];

export default function Toolbar({ activeTool, onSelectTool }: Props): JSX.Element {
    return (
        <View style={styles.container}>
            {tools.map((t) => (
                <TouchableOpacity
                    key={t}
                    style={[styles.button, activeTool === t && styles.active]}
                    onPress={() => onSelectTool(t)}
                >
                    <Text style={styles.label}>{t.substring(0, 2)}</Text>
                </TouchableOpacity>
            ))}
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flexDirection: "row",
        padding: 8,
        backgroundColor: "#eee",
    },
    button: {
        padding: 10,
        marginRight: 4,
        borderRadius: 8,
        backgroundColor: "#ccc",
    },
    active: {
        backgroundColor: "#999",
    },
    label: {
        fontSize: 12,
        fontWeight: "bold",
    },
});
