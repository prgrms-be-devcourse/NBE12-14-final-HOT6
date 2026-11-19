"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import Header from "@/components/Header";
import MapPicker, { type LatLng } from "@/components/MapPicker";
import { loadCtx, saveCtx } from "@/lib/devContext";
import { merchant } from "@/lib/mock";

export default function MerchantEditPage() {
  const [open, setOpen] = useState(true);
  const [editing, setEditing] = useState(false);
  const [f, setF] = useState({ name: merchant.name, address: merchant.address, phone: merchant.phone });
  const [eligible, setEligible] = useState(true);
  const [pos, setPos] = useState<LatLng | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const c = loadCtx();
    setF((x) => ({ ...x, name: c.merchantName ?? x.name, address: c.merchantAddress ?? x.address }));
    setEligible(c.eligible ?? true);
    if (c.lat !== undefined && c.lng !== undefined) setPos({ lat: c.lat, lng: c.lng });
    setReady(true);
  }, []);

  const save = () => {
    saveCtx({ merchantName: f.name, merchantAddress: f.address, ...(pos ? { lat: pos.lat, lng: pos.lng } : {}) });
    setEditing(false);
  };
  const ro = !editing;
  const input = (k: keyof typeof f, label: string) => (
    <label className="block"><span className="label">{label}</span>
      <input className={`field ${ro ? "bg-slate-50 text-slate-600" : ""}`} readOnly={ro} value={f[k]} onChange={(e) => setF((x) => ({ ...x, [k]: e.target.value }))} /></label>
  );

  return (
    <>
      <Header title="가맹점 정보 · 상태관리" />
      <div className="space-y-3 px-5 pb-5">
        <div className="card flex items-center justify-between !p-3.5">
          <div><div className="font-semibold">{f.name}</div><span className={`badge mt-1 ${eligible ? "badge-ok" : "badge-warn"}`}>{eligible ? "적립 가능 가맹점" : "적립 제외 가맹점"}</span></div>
          <span className={`badge ${open ? "badge-info" : "badge-bad"}`}>{open ? "운영중" : "중지"}</span>
        </div>
        {input("name", "가맹점명")}
        {input("address", "주소")}
        {input("phone", "연락처")}
        <div>
          <span className="label">지도</span>
          {ready && <MapPicker value={pos} onChange={editing ? setPos : undefined} />}
          <p className="note mt-1">{editing ? "지도를 눌러 위치를 바꿀 수 있어요" : pos ? "등록된 위치" : "위치가 등록되지 않았어요 (수정에서 선택)"}</p>
        </div>
        {editing
          ? <div className="flex gap-2.5"><button type="button" onClick={() => setEditing(false)} className="btn btn-outline">취소</button><button type="button" onClick={save} className="btn btn-primary">저장</button></div>
          : <button type="button" onClick={() => setEditing(true)} className="btn btn-outline">수정</button>}

        <div>
          <span className="label">운영 상태</span>
          <div className="flex gap-2" role="radiogroup">
            {[["운영중", true], ["중지", false]].map(([l, v]) => (
              <button key={String(l)} type="button" role="radio" aria-checked={open === v} onClick={() => setOpen(v as boolean)} className={`chip flex-1 ${open === v ? "chip-on" : ""}`}>{l}</button>
            ))}
          </div>
          <p className={`mt-2 text-[13px] ${open ? "text-slate-500" : "font-medium text-red-600"}`} role="status">중지 상태에서는 QR 결제가 거절됩니다</p>
        </div>
        <Link href="/merchant/qr" className="btn btn-primary">QR 생성하러 가기</Link>
        <p className="note">※ 상태관리 범위 확인 필요 · 수정/상태 변경 API 연동 전 (화면에서만 반영)</p>
      </div>
    </>
  );
}
