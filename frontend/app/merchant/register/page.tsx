"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import Header from "@/components/Header";
import MapPicker, { type LatLng } from "@/components/MapPicker";
import { ApiError } from "@/lib/api";
import { loadCtx, saveCtx } from "@/lib/devContext";
import { CATEGORIES } from "@/lib/stores";
import { registerMerchant } from "@/lib/endpoints";

export default function MerchantRegisterPage() {
  const router = useRouter();
  const [ownerId, setOwnerId] = useState(1);
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [phone, setPhone] = useState("");
  const [eligible, setEligible] = useState(true);
  const [category, setCategory] = useState("식당");
  const [pos, setPos] = useState<LatLng | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => setOwnerId(loadCtx().ownerId), []);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res = await registerMerchant({ ownerId, name: name.trim(), address: address.trim(), cashbackEligible: eligible });
      saveCtx({ merchantId: res.merchantId, merchantName: res.name, category, merchantAddress: res.address, eligible: res.cashbackEligible, ...(pos ? { lat: pos.lat, lng: pos.lng } : {}) });
      router.push("/merchant/edit");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "알 수 없는 오류가 발생했습니다");
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Header title="가맹점 등록" back="/merchant/login" />
      <form onSubmit={submit} className="space-y-3 px-5 pb-5">
        <label className="block"><span className="label">가맹점명</span><input className="field" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} /></label>
        <label className="block"><span className="label">주소</span><input className="field" required maxLength={200} value={address} onChange={(e) => setAddress(e.target.value)} /></label>
        <label className="block"><span className="label">업종 (지도 마크에 표시 · API 미지원 · 이 브라우저에만 저장)</span><select className="field" value={category} onChange={(e) => setCategory(e.target.value)}>{CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.icon} {c.value}</option>)}</select></label>
        <label className="block"><span className="label">연락처 (API 미지원 · 전송 안 함)</span><input className="field" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} /></label>
        <label className="block"><span className="label">ownerId (개발용 · 인증 연동 전 임시)</span><input className="field" inputMode="numeric" value={ownerId} onChange={(e) => setOwnerId(Number(e.target.value.replace(/\D/g, "")) || 0)} /></label>
        <label className="card flex cursor-pointer items-start gap-3 !p-3.5">
          <input type="checkbox" className="mt-1 h-4 w-4 accent-blue-600" checked={eligible} onChange={(e) => setEligible(e.target.checked)} />
          <span><span className="block text-sm font-semibold">캐시백 적립 대상 가맹점</span><span className="note">끄면 이 가맹점에서는 적립이 제외돼요. 캐시백 잔액 사용은 가능합니다.</span></span>
        </label>
        <div>
          <span className="label">지도 (위치 선택)</span>
          <MapPicker value={pos} onChange={setPos} />
          <p className="note mt-1">{pos ? `선택한 위치 ${pos.lat.toFixed(5)}, ${pos.lng.toFixed(5)} (좌표는 API 미지원 · 이 브라우저에만 저장)` : "지도를 눌러 가맹점 위치를 선택하세요"}</p>
        </div>
        {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-800">{error}</p>}
        <button type="submit" disabled={busy || !name.trim() || !address.trim()} className="btn btn-primary disabled:opacity-40">{busy ? "등록 중…" : "등록"}</button>
        <div className="card-dash"><ul className="space-y-0.5"><li>• POST /api/merchants 로 등록됩니다</li><li>• cashbackEligible 값이 함께 전송됩니다</li><li>• 등록 후 merchantId 가 개발용 식별자에 저장됩니다</li></ul></div>
      </form>
    </>
  );
}
