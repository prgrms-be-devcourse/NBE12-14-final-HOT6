"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import Header from "@/components/Header";
import MapPicker, { type LatLng } from "@/components/MapPicker";
import { ApiError } from "@/lib/api";
import { loadCtx, saveCtx } from "@/lib/devContext";
import { getMyMerchant, updateMerchant } from "@/lib/endpoints";
import { PHONE_RULE } from "@/lib/signupDraft";

export default function MerchantEditPage() {
  const [editing, setEditing] = useState(false);
  const [f, setF] = useState({ name: "", address: "", phone: "" });
  const [eligible, setEligible] = useState(true);
  const [pos, setPos] = useState<LatLng | null>(null);
  const [ready, setReady] = useState(false);
  const [loaded, setLoaded] = useState(false); // 서버에서 내 가맹점을 읽었을 때만 수정할 수 있다
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  // 이름·주소·적립 대상 여부는 서버에서, 위치는 이 브라우저에 저장된 값을 읽는다
  useEffect(() => {
    let alive = true;
    const c = loadCtx();
    if (c.lat !== undefined && c.lng !== undefined) setPos({ lat: c.lat, lng: c.lng });
    setF((x) => ({ ...x, phone: c.phone ?? "" }));
    setReady(true);
    getMyMerchant()
      .then((m) => {
        if (!alive) return;
        setF((x) => ({ ...x, name: m.name, address: m.address }));
        setEligible(m.cashbackEligible);
        setLoaded(true);
      })
      .catch((err) => {
        if (alive) setMsg({ ok: false, text: err instanceof ApiError ? err.message : "가맹점 정보를 불러오지 못했어요" });
      });
    return () => {
      alive = false;
    };
  }, []);

  // 이름·주소만 서버에 반영한다. 연락처·위치는 서버에 저장하지 않아 이 브라우저에만 둔다
  const save = async () => {
    setBusy(true);
    setMsg(null);
    try {
      const res = await updateMerchant({ name: f.name.trim(), address: f.address.trim() });
      saveCtx({ merchantName: res.name, merchantAddress: res.address, eligible: res.cashbackEligible, phone: f.phone.trim(), ...(pos ? { lat: pos.lat, lng: pos.lng } : {}) });
      setF((x) => ({ ...x, name: res.name, address: res.address }));
      setEligible(res.cashbackEligible);
      setEditing(false);
      setMsg({ ok: true, text: "가맹점 정보가 수정되었어요" });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof ApiError ? err.message : "알 수 없는 오류가 발생했습니다" });
    } finally {
      setBusy(false);
    }
  };
  const ro = !editing;
  const phoneInvalid = f.phone !== "" && !PHONE_RULE.test(f.phone); // 비워도 되지만 입력하면 회원가입과 같은 규칙을 따른다
  const input = (k: keyof typeof f, label: string) => (
    <label className="block"><span className="label">{label}</span>
      <input className={`field ${ro ? "bg-slate-50 text-slate-600" : ""}`} readOnly={ro} value={f[k]} onChange={(e) => setF((x) => ({ ...x, [k]: e.target.value }))} /></label>
  );

  return (
    <>
      <Header title="가맹점 정보" />
      <div className="space-y-3 px-5 pb-5">
        <div className="card !p-3.5">
          <div className="font-semibold">{f.name}</div>
          <span className={`badge mt-1 ${eligible ? "badge-ok" : "badge-warn"}`}>{eligible ? "적립 가능 가맹점" : "적립 제외 가맹점"}</span>
        </div>
        {input("name", "가맹점명")}
        {input("address", "주소")}
        <label className="block"><span className="label">연락처 (이 기기에만 저장돼요)</span>
          <input className={`field ${ro ? "bg-slate-50 text-slate-600" : ""}`} readOnly={ro} type="tel" inputMode="numeric" autoComplete="tel" placeholder="01012345678" maxLength={11} value={f.phone} aria-invalid={editing && phoneInvalid ? true : undefined} onChange={(e) => setF((x) => ({ ...x, phone: e.target.value.replace(/\D/g, "").slice(0, 11) }))} />
          {editing && phoneInvalid && <span className="note mt-1 block !text-red-600">하이픈 없이 숫자만 입력해 주세요 (예: 01012345678)</span>}
        </label>
        <div>
          <span className="label">지도 (이 기기에만 저장돼요)</span>
          {ready && <MapPicker value={pos} onChange={editing ? setPos : undefined} />}
          <p className="note mt-1">{editing ? "지도를 눌러 위치를 바꿀 수 있어요" : pos ? "등록된 위치" : "위치가 등록되지 않았어요 (수정에서 선택)"}</p>
        </div>
        {msg && <p role={msg.ok ? "status" : "alert"} className={`rounded-lg px-3 py-2 text-[13px] font-medium ${msg.ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-600"}`}>{msg.text}</p>}
        {editing
          ? <div className="flex gap-2.5"><button type="button" onClick={() => setEditing(false)} className="btn btn-outline">취소</button><button type="button" onClick={save} disabled={busy || !f.name.trim() || !f.address.trim() || phoneInvalid} className="btn btn-primary disabled:opacity-40">{busy ? "저장 중…" : "저장"}</button></div>
          : <button type="button" onClick={() => setEditing(true)} disabled={!loaded} className="btn btn-outline disabled:opacity-40">수정</button>}

        <Link href="/merchant/qr" className="btn btn-primary">QR 생성하러 가기</Link>
        <p className="note">가맹점명과 주소는 서버에 저장돼요. 연락처와 지도 위치는 이 기기에서만 보이고 서버에는 저장되지 않아요.</p>
      </div>
    </>
  );
}
