import { Tool } from '@/types/tool';
import {
    requireNativeComponent,
    ViewProps
  } from 'react-native';
  
  export interface DrawingBoardProps extends ViewProps {
    backgroundColor?: string;  // e.g. "#1f1f1f"
    strokeColor?: string;      // e.g. "#ffffff"
    strokeSize?: number;       // e.g. 3
    tool?: Tool;
  }
  
  export const DrawingBoard = requireNativeComponent<DrawingBoardProps>(
    'RNDrawingBoard'
  );
  