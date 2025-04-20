export type UUID = string;

export interface Metadata {
    name: string;
    author: string;
    size: "A4" | "A3" | "Letter" | string;
    orientation: "Portrait" | "Landscape";
    units: "mm" | "in" | string;
    skcVersion: string;
}

export interface Point {
    x: number;
    y: number;
    pressure: number; // 0…1
}

export interface BoundingBox {
    start: [number, number];
    dimensions: [number, number];
}

export interface PathShape {
    id: UUID;
    boundingBox: BoundingBox;
    thickness: number;
    points: Point[];
}

export interface VectorShapeBase {
    id: UUID;
    color?: string;
    thickness?: number;
}

export interface CircleShape extends VectorShapeBase {
    type: "circle";
    center: [number, number];
    radius: number;
}
export interface RectangleShape extends VectorShapeBase {
    type: "rectangle";
    start: [number, number];
    width: number;
    height: number;
}
export interface LineShape extends VectorShapeBase {
    type: "line";
    start: [number, number];
    end: [number, number];
}
export interface PolygonShape extends VectorShapeBase {
    type: "polygon";
    vertices: [number, number][];
}
export interface SvgShape extends VectorShapeBase {
    type: "svg";
    data: string; // raw SVG string
    position: [number, number];
    scale: number;
}

export type VectorShape =
    | CircleShape
    | RectangleShape
    | LineShape
    | PolygonShape
    | SvgShape;

export interface TextNode {
    id: UUID;
    label: string;
    value: string;
    position: [number, number];
    size: number; // font size
}

export interface Page {
    number: number;
    data: Record<UUID, TextNode>;
    shapes: Record<UUID, VectorShape>;
    paths: PathShape[];
}

export interface SketchDocument {
    metadata: Metadata;
    pages: Page[];
}

export type Tool =
    | "pencil"
    | "line"
    | "rectangle"
    | "ellipse"
    | "text"
    | "select"
    | "move"
    | "clear"
    | "scale";
    