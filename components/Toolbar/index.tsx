import React, { FC } from "react";
import { View, Switch, StyleSheet, Button, Text, TouchableOpacity, TextInput } from "react-native";
import { UseToolbar } from "./useToolbar";

interface ToolbarProps {
    functions: UseToolbar;
}

const Toolbar: FC<ToolbarProps> = ({ functions }) => {
    const {
        tool,
        setTool,
        thickness,
        setThickness,
        color,
        setColor,
        debugStrokePrediction,
        setDebugStrokePrediction
    } = functions;
    return (
        <View style={styles.toolbar}>
            <Button
                title="Draw"
                onPress={() => setTool('draw')}
                color={tool === 'draw' ? '#007AFF' : '#555555'}
            />
            <Button
                title="Erase"
                onPress={() => setTool('erase')}
                color={tool === 'erase' ? '#007AFF' : '#555555'}
            />
            <Button
                title="Select"
                onPress={() => setTool('select')}
                color={tool === 'select' ? '#007AFF' : '#555555'}
            />

            <View style={styles.thicknessControls}>
                <View style={styles.thicknessContainer}>
                    <TouchableOpacity
                        onPress={() => setThickness(Math.max(thickness - 1, 1))}
                        style={styles.button}
                    >
                        <Text style={styles.plusMinusbuttonText}>-</Text>
                    </TouchableOpacity>
                    <TextInput
                        style={styles.thicknessInput}
                        value={String(thickness)}
                        keyboardType="numeric"
                        onChangeText={(value) => {
                            const num = parseInt(value, 10);
                            if (!isNaN(num)) {
                                setThickness(num);
                            }
                        }}
                    />
                    <TouchableOpacity
                        onPress={() => setThickness(thickness + 1)}
                        style={styles.button}
                    >
                        <Text style={styles.plusMinusbuttonText}>+</Text>
                    </TouchableOpacity>
                </View>
            </View>

            <View style={styles.colorControls}>
                {[
                    { label: 'White', color: '#FFFFFF' },
                    { label: 'Pink', color: '#FF9ADF' },
                    { label: 'Blue', color: '#4DD9E6' },
                    { label: 'Yellow', color: '#F8FF77' },
                ].map((colorOption) => (
                    <TouchableOpacity
                        key={colorOption.label}
                        onPress={() => setColor(colorOption.color)}
                        style={[
                            styles.button,
                            {
                                backgroundColor: colorOption.color,
                                borderWidth: 1,
                                borderColor: color == colorOption.color ? '#000000' : 'transparent',
                            }
                        ]}
                    >
                        <Text style={[styles.buttonText]}>
                            {colorOption.label}
                        </Text>
                    </TouchableOpacity>
                ))}
            </View>
            <View>
                <Text>
                    Show predicted stroke
                </Text>
                <Switch

                    onValueChange={
                        () => setDebugStrokePrediction(!debugStrokePrediction)
                    }
                    value={debugStrokePrediction}
                />
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    toolbar: {
        flexDirection: 'row',
        justifyContent: 'space-around',
        alignItems: 'center',
        height: 50,
        backgroundColor: '#F0D0FF',
        borderBottomWidth: 1,
        borderBottomColor: '#CCCCCC',
    },
    thicknessControls: {
        marginHorizontal: 10,
        justifyContent: 'center',
    },
    thicknessContainer: {
        flexDirection: 'row',
        alignItems: 'center',
        borderColor: '#CCCCCC',
        borderWidth: 1,
        borderRadius: 4,
        padding: 4,
        backgroundColor: '#FFFFFF'
    },
    thicknessInput: {
        width: 50,
        textAlign: 'center',
        marginHorizontal: 8,
    },
    colorControls: {
        flexDirection: 'row',
    },
    // Added style for TouchableOpacity button container
    button: {
        paddingVertical: 8,
        paddingHorizontal: 12,
        borderRadius: 4,
        marginHorizontal: 4,
        justifyContent: 'center',
        alignItems: 'center'
    },
    // Added style for the text inside the button
    buttonText: {
        color: '#000000'
    },
    plusMinusbuttonText: {
        color: '#000000',
        fontSize: 20,
    },
});

export default Toolbar;
