"use client";

import { useEffect, useMemo, useState } from "react";
import Header from "@/components/Header";
import StoreMap from "@/components/StoreMap";
import { loadCtx } from "@/lib/devContext";
import { CATEGORIES, iconOf, STORES, type Category, type Store } from "@/lib/stores";

export default function StoresPage() {
  const [q, setQ] = useState("");
  const [cat, setCat] = useState<Category | "전체">("전체");
  const [onlyEligible, setOnlyEligible] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [mine, setMine] = useState<Store | null>(null);

  // 내가 등록한 가맹점(사장님 앱에서 등록)도 지도에 함께 표시
  useEffect(() => {
    const c = loadCtx();
    if (c.merchantName && c.lat !== undefined && c.lng !== undefined) {
      setMine({
        id: "mine", name: c.merchantName, category: (c.category as Category) ?? "기타", address: c.merchantAddress ?? "",
        lat: c.lat, lng: c.lng, eligible: c.eligible ?? true, open: true, mine: true,
      });
    }
  }, []);

  const all = useMemo(() => (mine ? [...STORES, mine] : STORES), [mine]);
  const list = useMemo(() => {
    const k = q.trim().toLowerCase();
    return all.filter((s) =>
      (cat === "전체" || s.category === cat) &&
      (!onlyEligible || s.eligible) &&
      (!k || s.name.toLowerCase().includes(k) || s.address.toLowerCase().includes(k)),
    );
  }, [all, q, cat, onlyEligible]);

  const sel = list.find((s) => s.id === selected) ?? null;

  return (
    <>
      <Header title="가맹점 찾기" />
      <div className="space-y-3 px-5 pb-4">
        <label className="block">
          <span className="sr-only">매장명으로 찾기</span>
          <input type="search" className="field" placeholder="매장명으로 찾기" value={q} onChange={(e) => setQ(e.target.value)} />
        </label>

        <div className="-mx-5 flex gap-2 overflow-x-auto px-5 pb-1" role="tablist" aria-label="업종">
          {(["전체", ...CATEGORIES.map((c) => c.value)] as const).map((c) => (
            <button key={c} type="button" role="tab" aria-selected={cat === c} onClick={() => setCat(c)} className={`chip shrink-0 ${cat === c ? "chip-on" : ""}`}>
              {c !== "전체" && <span className="mr-1" aria-hidden>{iconOf(c)}</span>}{c}
            </button>
          ))}
        </div>

        <StoreMap stores={list} selectedId={selected} onSelect={setSelected} height={280} />

        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
          <span className="flex items-center gap-3">
            <span className="flex items-center gap-1"><i className="inline-block h-3 w-3 rounded-full border-2 border-emerald-500 bg-white" />적립 가능</span>
            <span className="flex items-center gap-1"><i className="inline-block h-3 w-3 rounded-full border-2 border-slate-400 bg-white" />적립 제외</span>
          </span>
          <label className="flex cursor-pointer items-center gap-1.5"><input type="checkbox" className="h-3.5 w-3.5 accent-blue-600" checked={onlyEligible} onChange={(e) => setOnlyEligible(e.target.checked)} />적립 가능만 보기</label>
        </div>

        {sel && (
          <section className="card border-blue-300 !p-3.5" aria-live="polite">
            <div className="flex items-start justify-between gap-2">
              <div>
                <div className="flex items-center gap-2 font-semibold"><span aria-hidden>{iconOf(sel.category)}</span>{sel.name}{sel.mine && <span className="badge badge-info">내 가맹점</span>}</div>
                <div className="note mt-0.5">{sel.category} · {sel.address || "주소 정보 없음"}</div>
              </div>
              <span className="badge badge-ok shrink-0">가맹점</span>
            </div>
            <div className="mt-2 flex flex-wrap gap-1.5">
              <span className={`badge ${sel.eligible ? "badge-ok" : "badge-warn"}`}>{sel.eligible ? "캐시백 적립 가능" : "적립 제외 (캐시백 사용 가능)"}</span>
              <span className={`badge ${sel.open ? "badge-info" : "badge-bad"}`}>{sel.open ? "운영중" : "운영 중지"}</span>
            </div>
          </section>
        )}

        <h2 className="px-1 pt-1 text-sm font-semibold">검색 결과 {list.length}곳</h2>
        {list.length === 0 ? (
          <p className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">찾으시는 가맹점이 없어요. 다른 이름이나 업종으로 찾아보세요.</p>
        ) : (
          <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
            {list.map((s) => (
              <li key={s.id}>
                <button type="button" onClick={() => setSelected(s.id)} className={`flex w-full items-center gap-3 px-3.5 py-3 text-left ${selected === s.id ? "bg-blue-50/60" : "hover:bg-slate-50"}`}>
                  <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full border-2 bg-white text-base ${s.eligible ? "border-emerald-500" : "border-slate-400"}`} aria-hidden>{iconOf(s.category)}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{s.name}{s.mine && <span className="badge badge-info ml-1.5">내 가맹점</span>}</span>
                    <span className="block truncate text-xs text-slate-500">{s.category} · {s.address}</span>
                  </span>
                  <span className={`badge shrink-0 ${s.eligible ? "badge-ok" : "badge-warn"}`}>{s.eligible ? "적립" : "제외"}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
        <p className="note">가맹점 목록 API 연동 전이라 예시 데이터와 내가 등록한 가맹점이 표시됩니다.</p>
      </div>
    </>
  );
}
