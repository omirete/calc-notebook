import { SafeAreaView, StyleSheet } from 'react-native';
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
          tool={functions.tool}
          thickness={functions.thickness}
          color={functions.color}
          boardColor='#1E1E1E'
          style={{ flex: 1, width: '100%', height: '100%' }}
          predictionMultiplier={functions.mult}
          predictedStrokeColor={functions.debugStrokePrediction ? '#FF00FF' : undefined}
          predictAfterNPoints={30}
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
});