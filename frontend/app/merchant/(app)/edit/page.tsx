"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import Header from "@/components/Header";
import LogoutLink from "@/components/LogoutLink";
import AddressSearch from "@/components/AddressSearch";
import NaverMap, { type MapPoint } from "@/components/NaverMap";
import { ApiError } from "@/lib/api";
import { saveCtx } from "@/lib/devContext";
import { getMyMerchant, updateMerchant } from "@/lib/endpoints";

const toPoint = (lat: number | null | undefined, lng: number | null | undefined, title: string): MapPoint | null =>
  typeof lat === "number" && typeof lng === "number" ? { lat, lng, title } : null;

export default function MerchantEditPage() {
  const [editing, setEditing] = useState(false);
  const [f, setF] = useState({ name: "", address: "" });
  const [eligible, setEligible] = useState(true);
  const [pos, setPos] = useState<MapPoint | null>(null);
  const [loaded, setLoaded] = useState(false); // 서버에서 내 가맹점을 읽었을 때만 수정할 수 있다
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  // 이름·주소·위치·적립 대상 여부를 서버에서 읽는다
  useEffect(() => {
    let alive = true;
    getMyMerchant()
      .then((m) => {
        if (!alive) return;
        setF((x) => ({ ...x, name: m.name, address: m.address }));
        setEligible(m.cashbackEligible);
        setPos(toPoint(m.latitude, m.longitude, m.name));
        setLoaded(true);
      })
      .catch((err) => {
        if (alive) setMsg({ ok: false, text: err instanceof ApiError ? err.message : "가맹점 정보를 불러오지 못했어요" });
      });
    return () => {
      alive = false;
    };
  }, []);

  // 주소가 바뀌면 서버가 새 주소로 위치를 다시 정해 응답으로 돌려준다
  const save = async () => {
    setBusy(true);
    setMsg(null);
    try {
      const res = await updateMerchant({ name: f.name.trim(), address: f.address.trim() });
      saveCtx({ merchantName: res.name, merchantAddress: res.address, eligible: res.cashbackEligible });
      setF((x) => ({ ...x, name: res.name, address: res.address }));
      setEligible(res.cashbackEligible);
      setPos(toPoint(res.latitude, res.longitude, res.name));
      setEditing(false);
      setMsg({ ok: true, text: "가맹점 정보가 수정되었어요" });
    } catch (err) {
      setMsg({ ok: false, text: err instanceof ApiError ? err.message : "알 수 없는 오류가 발생했습니다" });
    } finally {
      setBusy(false);
    }
  };
  const ro = !editing;
  const input = (k: keyof typeof f, label: string) => (
    <label className="block"><span className="label">{label}</span>
      <input className={`field ${ro ? "bg-slate-50 text-slate-600" : ""}`} readOnly={ro} value={f[k]} onChange={(e) => setF((x) => ({ ...x, [k]: e.target.value }))} /></label>
  );

  return (
    <>
      <Header title="마이페이지" />
      <div className="space-y-3 px-5 pb-5">
        <div className="card !p-3.5">
          <div className="font-semibold">{f.name}</div>
          <span className={`badge mt-1 ${eligible ? "badge-ok" : "badge-warn"}`}>{eligible ? "적립 가능 가맹점" : "적립 제외 가맹점"}</span>
        </div>
        {input("name", "가맹점명")}
        <div>
          <span className="label">주소</span>
          <div className="flex flex-wrap items-center gap-2">
            <input className={`field min-w-0 flex-1 ${ro ? "bg-slate-50 text-slate-600" : ""}`} readOnly value={f.address} aria-label="주소" />
            {editing && <AddressSearch onSelect={(a) => setF((x) => ({ ...x, address: a }))} />}
          </div>
        </div>
        <div>
          <span className="label">위치</span>
          {pos ? <NaverMap points={[pos]} /> : <p className="note">위치가 아직 없어요. 수정에서 주소를 저장하면 위치를 찾아요.</p>}
          <p className="note mt-1">주소를 바꿔 저장하면 위치도 새 주소로 바뀌어요.</p>
        </div>
        {msg && <p role={msg.ok ? "status" : "alert"} className={`rounded-lg px-3 py-2 text-[13px] font-medium ${msg.ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-600"}`}>{msg.text}</p>}
        {editing
          ? <div className="flex gap-2.5"><button type="button" onClick={() => setEditing(false)} className="btn btn-outline">취소</button><button type="button" onClick={save} disabled={busy || !f.name.trim() || !f.address.trim()} className="btn btn-primary disabled:opacity-40">{busy ? "저장 중…" : "저장"}</button></div>
          : <button type="button" onClick={() => setEditing(true)} disabled={!loaded} className="btn btn-outline disabled:opacity-40">수정</button>}

        <Link href="/merchant/closure" className="btn btn-danger">가맹점 해지</Link>
        <LogoutLink href="/merchant/login" className="btn btn-outline">로그아웃</LogoutLink>
        <p className="note">가맹점명, 주소, 위치는 서버에 저장돼요.</p>
      </div>
    </>
  );
}
