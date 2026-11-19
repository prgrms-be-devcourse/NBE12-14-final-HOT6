import type { Endpoint } from "@/lib/apiCatalog";

/* eslint-disable @typescript-eslint/no-explicit-any */
type Doc = any;

function example(schema: any, doc: Doc, depth = 0): unknown {
  if (!schema || depth > 5) return null;
  if (schema.$ref) {
    const name = String(schema.$ref).split("/").pop()!;
    return example(doc.components?.schemas?.[name], doc, depth + 1);
  }
  if (schema.example !== undefined) return schema.example;
  if (schema.enum) return schema.enum[0];
  switch (schema.type) {
    case "object": {
      const out: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(schema.properties ?? {})) out[k] = example(v, doc, depth + 1);
      return out;
    }
    case "array": return [example(schema.items, doc, depth + 1)];
    case "integer": case "number": return 1;
    case "boolean": return true;
    case "string": return schema.format === "date-time" ? new Date().toISOString().slice(0, 19) : "string";
    default: return null;
  }
}

const METHODS = ["get", "post", "put", "patch", "delete"] as const;

export function endpointsFromOpenApi(doc: Doc): Endpoint[] {
  const out: Endpoint[] = [];
  for (const [path, item] of Object.entries<any>(doc.paths ?? {})) {
    for (const m of METHODS) {
      const op = item?.[m];
      if (!op) continue;
      const method = m.toUpperCase() as Endpoint["method"];
      const schema = op.requestBody?.content?.["application/json"]?.schema;
      out.push({
        id: `${method} ${path}`, method, path, tag: op.tags?.[0] ?? "기타", summary: op.summary ?? "",
        body: schema ? JSON.stringify(example(schema, doc), null, 2) : undefined,
        idempotency: (op.parameters ?? []).some((p: any) => p.in === "header" && /idempotency-key/i.test(p.name)),
      });
    }
  }
  return out;
}
