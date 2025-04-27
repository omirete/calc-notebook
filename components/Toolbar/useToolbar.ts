import { Tool } from "@/types/tool";
import { useState } from "react";

export interface UseToolbar {
    tool: Tool;
    setTool: (tool: Tool) => void;
    thickness: number;
    setThickness: (thickness: number) => void;
    color: string;
    setColor: (color: string) => void;
    mult: number;
    setMult: (mult: number) => void;
    debugStrokePrediction: boolean;
    setDebugStrokePrediction: (debugStrokePrediction: boolean) => void;
}

const useToolbar = (): UseToolbar => {
    const [tool, setTool] = useState<Tool>('draw');
    const [thickness, setThickness] = useState<number>(2);
    const [color, setColor] = useState<string>('#FFFFFF');
    const [mult, setMult] = useState<number>(5);
    const [debugStrokePrediction, setDebugStrokePrediction] = useState<boolean>(false);
    return {
        tool,
        setTool,
        thickness,
        setThickness,
        color,
        setColor,
        mult,
        setMult,
        debugStrokePrediction,
        setDebugStrokePrediction,
    }
}

export default useToolbar;
