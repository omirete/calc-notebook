// App.js - Example usage
import React, { useState } from 'react';
import { SafeAreaView, View, Button, StyleSheet, Text } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { DrawingBoard, Tool } from '@/components/DrawingBoardWrapper';

export default function App() {
  const [tool, setTool] = useState<Tool>('draw');
  const [thickness, setThickness] = useState(2);
  const [color, setColor] = useState('#FFFFFF');

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaView style={styles.container}>
        <View style={styles.toolbar}>
          <Text>Tool: {tool}</Text>
          <Text>Thickness: {thickness}</Text>
          <Text>Color: {color}</Text>
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
            {['Thin', 'Medium', 'Thick'].map((label, index) => (
              <Button
                key={index}
                title={label}
                onPress={() => setThickness(index + 1)}
                color={thickness === index + 1 ? '#007AFF' : '#555555'}
              />
            ))}
          </View>

          <View style={styles.colorControls}>
            {[{ label: 'White', color: '#FFFFFF' }, { label: 'Red', color: '#FF0000' }, { label: 'Blue', color: '#0000FF' }].map((colorOption) => (
              <Button
                key={colorOption.label}
                title={colorOption.label}
                onPress={() => setColor(colorOption.color)}
                color={color === colorOption.color ? '#007AFF' : '#555555'}
              />
            ))}
          </View>
        </View>
        <DrawingBoard
          tool={tool}
          thickness={thickness}
          color={color}
          boardColor='#1E1E1E'
          style={{ flex: 1, width: '100%', height: '100%' }}
        />
      </SafeAreaView>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    // backgroundColor: '#FFFFFF',
  },
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
    flexDirection: 'row',
  },
  colorControls: {
    flexDirection: 'row',
  },
});