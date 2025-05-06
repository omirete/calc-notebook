import React from 'react';
import { SafeAreaView, StyleSheet, Button, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { DrawingBoard } from '@/components/DrawingBoardWrapper';
import Toolbar from '@/components/Toolbar';
import useToolbar from '@/components/Toolbar/useToolbar';

export default function App() {
  const functions = useToolbar();
  
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaView style={styles.container}>
        <Toolbar functions={functions} />
        <DrawingBoard
          strokeColor={functions.color}
          strokeSize={functions.thickness}
          backgroundColor="#1F1F1F"
          style={{ flex: 1, width: '100%', height: '100%' }}
          // predictedStrokeColor={functions.debugStrokePrediction ? '#FF00FF' : undefined}
        />
      </SafeAreaView>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    padding: 10,
  }
});