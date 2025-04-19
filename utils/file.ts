import * as FileSystem from "expo-file-system";
import { SketchDocument, Page } from "@/types/sketch";

// Very naive encoder ‑ lines with # Metadata / # Page headers, similar
// to the format given by the user. Adjust if you need stronger guarantees.
export const encodeSketch = (doc: SketchDocument): string => {
  const lines: string[] = [];
  lines.push("# Metadata");
  const m = doc.metadata;
  lines.push(`Name: ${m.name}`);
  lines.push(`Author: ${m.author}`);
  lines.push(`Size: ${m.size}`);
  lines.push(`Orientation: ${m.orientation}`);
  lines.push(`Units: ${m.units}`);
  lines.push(`SKC Version: ${m.skcVersion}`);
  doc.pages.forEach((p) => {
    encodePage(p, lines);
  });
  return lines.join("\n");
};

const encodePage = (page: Page, out: string[]): void => {
  out.push("\n# Page " + page.number);
  out.push("## Data");
  Object.entries(page.data).forEach(([id, node]) => {
    out.push(`${id} = ${JSON.stringify(node)}`);
  });
  out.push("\n## Shapes (images, vectors, simple geometries)");
  Object.entries(page.shapes).forEach(([id, shape]) => {
    out.push(`${id} = ${JSON.stringify(shape)}`);
  });
  out.push("\n## Paths ");
  page.paths.forEach((p) => out.push(JSON.stringify(p)));
};

export const decodeSketch = (content: string): SketchDocument => {
  // For brevity we use a simple regex‑based parser. Production code
  // should use a proper grammar or split by sections.
  const lines = content.split(/\r?\n/);
  const metadata: any = {};
  const pages: Page[] = [];
  let currentPage: Page | null = null;
  let section: "metadata" | "data" | "shapes" | "paths" | null = null;

  lines.forEach((line) => {
    if (line.startsWith("# Metadata")) {
      section = "metadata";
      return;
    }
    if (line.startsWith("# Page")) {
      if (currentPage) pages.push(currentPage);
      const num = parseInt(line.match(/# Page (\d+)/)?.[1] ?? "1", 10);
      currentPage = {
        number: num,
        data: {},
        shapes: {},
        paths: [],
      } as Page;
      section = null;
      return;
    }
    if (line.startsWith("## Data")) {
      section = "data";
      return;
    }
    if (line.startsWith("## Shapes")) {
      section = "shapes";
      return;
    }
    if (line.startsWith("## Paths")) {
      section = "paths";
      return;
    }
    if (line.trim() === "") return;

    switch (section) {
      case "metadata": {
        const [key, ...rest] = line.split(":");
        metadata[key.trim().toLowerCase()] = rest.join(":").trim();
        break;
      }
      case "data": {
        const [id, json] = line.split("=");
        if (currentPage)
          currentPage.data[id.trim()] = JSON.parse(json.trim());
        break;
      }
      case "shapes": {
        const [id, json] = line.split("=");
        if (currentPage)
          currentPage.shapes[id.trim()] = JSON.parse(json.trim());
        break;
      }
      case "paths": {
        if (currentPage) currentPage.paths.push(JSON.parse(line.trim()));
        break;
      }
    }
  });
  if (currentPage) pages.push(currentPage);
  return {
    metadata: {
      name: metadata["name"],
      author: metadata["author"],
      size: metadata["size"],
      orientation: metadata["orientation"],
      units: metadata["units"],
      skcVersion: metadata["skc version"],
    },
    pages,
  } as SketchDocument;
};

export const saveSketch = async (
  doc: SketchDocument,
  filename: string = "note.skc"
): Promise<string> => {
  const path = FileSystem.documentDirectory + filename;
  await FileSystem.writeAsStringAsync(path, encodeSketch(doc), {
    encoding: FileSystem.EncodingType.UTF8,
  });
  return path;
};

export const loadSketch = async (path: string): Promise<SketchDocument> => {
  const content = await FileSystem.readAsStringAsync(path, {
    encoding: FileSystem.EncodingType.UTF8,
  });
  return decodeSketch(content);
};
