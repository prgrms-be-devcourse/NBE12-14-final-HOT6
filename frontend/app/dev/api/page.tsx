"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import Logo from "@/components/Logo";
import { BASE_CATALOG, type Endpoint } from "@/lib/apiCatalog";
import { endpointsFromOpenApi } from "@/lib/openapi";

type Result = { status: number; statusText: string; ms: number; body: string; ok: boolean } | { error: string };

const METHOD_COLOR: Record<string, string> = {
  GET: "bg-blue-100 text-blue-800", POST: "bg-emerald-100 text-emerald-800", PUT: "bg-amber-100 text-amber-800",
  PATCH: "bg-amber-100 text-amber-800", DELETE: "bg-red-100 text-red-800",
};

export default function ApiTesterPage() {
  const [catalog, setCatalog] = useState<Endpoint[]>(BASE_CATALOG);
  const [docState, setDocState] = useState("OpenAPI 문서 확인 중…");
  const [sel, setSel] = useState<Endpoint>(BASE_CATALOG[0]);
  const [method, setMethod] = useState<string>(BASE_CATALOG[0].method);
  const [path, setPath] = useState(BASE_CATALOG[0].path);
  const [body, setBody] = useState(BASE_CATALOG[0].body ?? "");
  const [idemKey, setIdemKey] = useState("");
  const [auth, setAuth] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<Result | null>(null);

  useEffect(() => {
    fetch("/v3/api-docs")
      .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
      .then((doc) => {
        const fromDoc = endpointsFromOpenApi(doc);
        const known = new Set(fromDoc.map((e) => e.id));
        setCatalog([...fromDoc, ...BASE_CATALOG.filter((e) => !known.has(e.id))]);
        setDocState(`OpenAPI 문서에서 ${fromDoc.length}개 불러옴`);
      })
      .catch((s) => setDocState(`OpenAPI 문서를 읽지 못했습니다 (${s}) — 기본 목록만 표시합니다`));
  }, []);

  const grouped = useMemo(() => {
    const m = new Map<string, Endpoint[]>();
    for (const e of catalog) m.set(e.tag, [...(m.get(e.tag) ?? []), e]);
    return [...m.entries()];
  }, [catalog]);

  const pick = (e: Endpoint) => {
    setSel(e); setMethod(e.method); setPath(e.path); setBody(e.body ?? ""); setResult(null);
    setIdemKey(e.idempotency ? crypto.randomUUID() : "");
  };

  const send = async () => {
    setBusy(true); setResult(null);
    const headers: Record<string, string> = {};
    const hasBody = body.trim() !== "" && method !== "GET";
    if (hasBody) headers["Content-Type"] = "application/json";
    if (idemKey.trim()) headers["Idempotency-Key"] = idemKey.trim();
    if (auth.trim()) headers["Authorization"] = auth.trim();
    const t0 = performance.now();
    try {
      const res = await fetch(path, { method, headers, body: hasBody ? body : undefined });
      const text = await res.text();
      let pretty = text;
      try { pretty = JSON.stringify(JSON.parse(text), null, 2); } catch { /* 본문이 JSON 이 아님 */ }
      setResult({ status: res.status, statusText: res.statusText, ms: Math.round(performance.now() - t0), body: pretty || "(빈 본문)", ok: res.ok });
    } catch {
      setResult({ error: "백엔드에 연결할 수 없습니다. 서버가 켜져 있는지, BACKEND_URL 이 맞는지 확인해 주세요." });
    } finally { setBusy(false); }
  };

  return (
    <div className="min-h-screen">
      <header className="flex items-center justify-between border-b border-zinc-300 bg-white px-5 py-3">
        <div className="flex items-center gap-3"><Logo className="text-lg" /><span className="text-sm text-zinc-500">API 테스트</span></div>
        <Link href="/dev" className="text-xs text-zinc-500 underline-offset-2 hover:underline">화면 목록</Link>
      </header>
      <div className="mx-auto grid max-w-6xl gap-4 p-5 lg:grid-cols-[280px_1fr]">
        <aside className="card !p-3 lg:self-start">
          <p className="note mb-2 px-1">{docState}</p>
          {grouped.map(([tag, list]) => (
            <div key={tag} className="mb-3">
              <div className="px-1 pb-1 text-xs font-semibold text-zinc-500">{tag}</div>
              {list.map((e) => (
                <button key={e.id} type="button" onClick={() => pick(e)} className={`flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm hover:bg-zinc-100 ${sel.id === e.id ? "bg-zinc-100" : ""}`}>
                  <span className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${METHOD_COLOR[e.method]}`}>{e.method}</span>
                  <span className="min-w-0"><span className="block truncate font-mono text-[12px]">{e.path}</span>{e.summary && <span className="block truncate text-[11px] text-zinc-500">{e.summary}</span>}</span>
                </button>
              ))}
            </div>
          ))}
        </aside>

        <main className="space-y-4">
          <section className="card space-y-3">
            <div className="flex gap-2">
              <select aria-label="HTTP 메서드" className="field !w-28 font-semibold" value={method} onChange={(e) => setMethod(e.target.value)}>
                {["GET", "POST", "PUT", "PATCH", "DELETE"].map((m) => <option key={m}>{m}</option>)}
              </select>
              <input aria-label="요청 경로" className="field font-mono text-sm" value={path} onChange={(e) => setPath(e.target.value)} />
              <button type="button" onClick={send} disabled={busy || !path.startsWith("/")} className="btn btn-primary !w-28 disabled:opacity-40">{busy ? "전송 중…" : "전송"}</button>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block"><span className="label">Idempotency-Key</span>
                <div className="flex gap-2"><input className="field font-mono text-xs" value={idemKey} onChange={(e) => setIdemKey(e.target.value)} placeholder="필요한 API 만 입력" />
                  <button type="button" className="chip" onClick={() => setIdemKey(crypto.randomUUID())}>생성</button></div></label>
              <label className="block"><span className="label">Authorization (JWT 연동 후)</span>
                <input className="field font-mono text-xs" value={auth} onChange={(e) => setAuth(e.target.value)} placeholder="Bearer eyJ..." /></label>
            </div>
            <label className="block"><span className="label">요청 본문 (JSON)</span>
              <textarea className="field h-48 resize-y font-mono text-[13px]" spellCheck={false} value={body} onChange={(e) => setBody(e.target.value)} /></label>
          </section>

          <section className="card" aria-live="polite">
            <h2 className="mb-2 font-semibold">응답</h2>
            {!result && <p className="note">전송하면 응답이 여기에 표시됩니다.</p>}
            {result && "error" in result && <p role="alert" className="text-sm font-medium text-red-800">{result.error}</p>}
            {result && "status" in result && (
              <>
                <div className="mb-2 flex items-center gap-2 text-sm">
                  <span className={`badge ${result.ok ? "badge-ok" : "badge-bad"}`}>{result.status} {result.statusText}</span>
                  <span className="note">{result.ms} ms</span>
                </div>
                <pre className="max-h-96 overflow-auto rounded-lg bg-zinc-900 p-3 font-mono text-[12px] leading-relaxed text-zinc-100">{result.body}</pre>
              </>
            )}
          </section>
        </main>
      </div>
    </div>
  );
}
