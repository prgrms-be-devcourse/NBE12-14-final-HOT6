"use client";

import "leaflet/dist/leaflet.css";
import { useEffect, useRef } from "react";

export type LatLng = { lat: number; lng: number };
type Props = { value: LatLng | null; onChange?: (p: LatLng) => void; height?: number };

const SEOUL: LatLng = { lat: 37.5665, lng: 126.978 };

// OpenStreetMap + Leaflet (API 키 불필요). onChange 가 없으면 보기 전용.
export default function MapPicker({ value, onChange, height = 180 }: Props) {
  const el = useRef<HTMLDivElement>(null);
  const state = useRef<{ map: import("leaflet").Map; marker: import("leaflet").CircleMarker | null; L: typeof import("leaflet") } | null>(null);
  const cb = useRef(onChange);
  cb.current = onChange;

  useEffect(() => {
    let dead = false;
    (async () => {
      const L = await import("leaflet");
      if (dead || !el.current) return;
      const c = value ?? SEOUL;
      const map = L.map(el.current, { zoomControl: true, attributionControl: true }).setView([c.lat, c.lng], 15);
      L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      }).addTo(map);
      const mk = (p: LatLng) => L.circleMarker([p.lat, p.lng], { radius: 9, color: "#fff", weight: 3, fillColor: "#2563eb", fillOpacity: 1 }).addTo(map);
      state.current = { map, marker: value ? mk(value) : null, L };
      if (cb.current) {
        map.on("click", (e) => {
          const p = { lat: e.latlng.lat, lng: e.latlng.lng };
          const s = state.current!;
          s.marker?.remove();
          s.marker = mk(p);
          cb.current?.(p);
        });
      }
    })();
    return () => {
      dead = true;
      state.current?.map.remove();
      state.current = null;
    };
    // 지도는 한 번만 만든다 (value 변경은 아래 effect 에서 반영)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const s = state.current;
    if (!s || !value) return;
    s.marker?.remove();
    s.marker = s.L.circleMarker([value.lat, value.lng], { radius: 9, color: "#fff", weight: 3, fillColor: "#2563eb", fillOpacity: 1 }).addTo(s.map);
  }, [value]);

  return <div ref={el} style={{ height }} className="relative z-0 isolate w-full overflow-hidden rounded-xl border border-slate-300" aria-label="지도" />;
}
