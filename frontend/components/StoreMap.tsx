"use client";

import "leaflet/dist/leaflet.css";
import { useEffect, useRef } from "react";
import { iconOf, type Store } from "@/lib/stores";

type Props = { stores: Store[]; selectedId: string | null; onSelect: (id: string | null) => void; height?: number };
type LMap = import("leaflet").Map;
type LMarker = import("leaflet").Marker;

const pinHtml = (s: Store, selected: boolean) => {
  const ring = s.eligible ? "#10b981" : "#94a3b8"; // 초록=적립 가능, 회색=적립 제외
  const size = selected ? 40 : 32;
  return `<div style="width:${size}px;height:${size}px;border-radius:50%;background:#fff;border:3px solid ${selected ? "#2563eb" : ring};display:flex;align-items:center;justify-content:center;font-size:${selected ? 20 : 16}px;box-shadow:0 1px 4px rgba(0,0,0,.35);opacity:${s.open ? 1 : 0.55}">${iconOf(s.category)}</div>`;
};

// 가맹점 마커 지도. 마커 = 업종 아이콘, 테두리 = 적립 가능(초록)/제외(회색), 선택 = 파랑
export default function StoreMap({ stores, selectedId, onSelect, height = 280 }: Props) {
  const el = useRef<HTMLDivElement>(null);
  const ref = useRef<{ map: LMap; L: typeof import("leaflet"); markers: Map<string, LMarker> } | null>(null);
  const select = useRef(onSelect);
  select.current = onSelect;
  const latest = useRef({ stores, selectedId });
  latest.current = { stores, selectedId };

  const draw = () => {
    const r = ref.current;
    if (!r) return;
    const { stores, selectedId } = latest.current;
    r.markers.forEach((m) => m.remove());
    r.markers.clear();
    for (const s of stores) {
      const sel = s.id === selectedId;
      const size = sel ? 40 : 32;
      const m = r.L.marker([s.lat, s.lng], {
        icon: r.L.divIcon({ html: pinHtml(s, sel), className: "", iconSize: [size, size], iconAnchor: [size / 2, size / 2] }),
        title: s.name,
        zIndexOffset: sel ? 1000 : 0,
        keyboard: true,
      }).addTo(r.map);
      m.on("click", () => select.current(s.id));
      r.markers.set(s.id, m);
    }
  };

  useEffect(() => {
    let dead = false;
    (async () => {
      const L = await import("leaflet");
      if (dead || !el.current) return;
      const map = L.map(el.current).setView([37.5665, 126.979], 15);
      L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      }).addTo(map);
      map.on("click", () => select.current(null));
      ref.current = { map, L, markers: new Map() };
      draw();
      fit();
    })();
    return () => {
      dead = true;
      ref.current?.map.remove();
      ref.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fit = () => {
    const r = ref.current;
    const list = latest.current.stores;
    if (!r || list.length === 0) return;
    r.map.fitBounds(r.L.latLngBounds(list.map((s) => [s.lat, s.lng] as [number, number])), { padding: [36, 36], maxZoom: 16 });
  };

  // 검색·필터로 목록이 바뀌면 다시 그리고 범위를 맞춘다
  useEffect(() => {
    draw();
    fit();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stores]);

  // 선택이 바뀌면 마커 강조 + 그 위치로 이동
  useEffect(() => {
    draw();
    const r = ref.current;
    const s = stores.find((x) => x.id === selectedId);
    if (r && s) r.map.flyTo([s.lat, s.lng], Math.max(r.map.getZoom(), 16), { duration: 0.4 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  return <div ref={el} style={{ height }} className="relative z-0 isolate w-full overflow-hidden rounded-xl border border-slate-300" aria-label="가맹점 지도" />;
}
