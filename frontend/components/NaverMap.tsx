"use client";

import { useEffect, useRef, useState } from "react";
import { NAVER_AUTH_FAILURE_EVENT, loadNaverMaps, naverMapConfigured } from "@/lib/naverMap";

export type MapPoint = { lat: number; lng: number; title?: string };
type Props = { points: MapPoint[]; height?: number; zoom?: number };

const SEOUL: MapPoint = { lat: 37.5665, lng: 126.978 };

// 읽기 전용 지도. 위치는 서버가 주소로 변환해 저장한 값을 마커로만 보여 준다.
export default function NaverMap({ points, height = 200, zoom = 16 }: Props) {
  const el = useRef<HTMLDivElement>(null);
  const [error, setError] = useState("");
  const key = points.map((p) => `${p.lat},${p.lng},${p.title ?? ""}`).join("|");

  useEffect(() => {
    let dead = false;
    let destroy: (() => void) | null = null;
    const onAuthFailure = () => setError("지도를 사용할 수 없어요 (인증 실패)");
    window.addEventListener(NAVER_AUTH_FAILURE_EVENT, onAuthFailure);
    loadNaverMaps()
      .then((maps) => {
        if (dead || !el.current) return;
        const first = points[0] ?? SEOUL;
        const map = new maps.Map(el.current, { center: new maps.LatLng(first.lat, first.lng), zoom, scaleControl: false, mapDataControl: false });
        const markers = points.map((p) => new maps.Marker({ position: new maps.LatLng(p.lat, p.lng), map, title: p.title }));
        if (points.length > 1) {
          const bounds = new maps.LatLngBounds();
          points.forEach((p) => bounds.extend(new maps.LatLng(p.lat, p.lng)));
          map.fitBounds(bounds, { top: 24, right: 24, bottom: 24, left: 24 });
        }
        destroy = () => {
          markers.forEach((m) => m.setMap(null));
          map.destroy();
        };
      })
      .catch((e) => {
        if (!dead) setError(e instanceof Error ? e.message : "지도를 불러오지 못했어요");
      });
    return () => {
      dead = true;
      window.removeEventListener(NAVER_AUTH_FAILURE_EVENT, onAuthFailure);
      destroy?.();
    };
    // 좌표가 실제로 바뀔 때만 지도를 다시 만든다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, zoom]);

  if (!naverMapConfigured || error) {
    return (
      <div style={{ height }} className="flex w-full items-center justify-center rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 text-center text-sm text-slate-500">
        {error || "지도를 사용할 수 없어요"}
      </div>
    );
  }
  return <div ref={el} style={{ height }} className="naver-map relative z-0 isolate w-full overflow-hidden rounded-xl border border-slate-300" aria-label="지도" />;
}
