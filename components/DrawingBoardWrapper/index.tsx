import { Tool } from '@/types/tool';
import { forwardRef } from 'react';
import {
    requireNativeComponent,
    type HostComponent,
    type ViewProps,
} from 'react-native';

type Props = ViewProps & {
    tool?: Tool;
    thickness?: number;
    color?: string;
    boardColor?: string;
    predictionMultiplier?: number;
    predictedStrokeColor?: string;
    predictAfterNPoints?: number;
};

const COMPONENT = 'RNDrawingBoard';

// ⬇️  reuse the wrapper if it already exists (Fast-Refresh safe)
const Native: HostComponent<Props> =
    (global as any).__RNDrawingBoard__ ??
    ((global as any).__RNDrawingBoard__ =
        requireNativeComponent<Props>(COMPONENT));

export const DrawingBoard = forwardRef<HostComponent<Props>, Props>(
    (props, ref) => <Native ref={ref as any} {...props} />
);

DrawingBoard.displayName = 'DrawingBoard';
