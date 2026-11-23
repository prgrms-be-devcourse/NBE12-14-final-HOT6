"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import Header from "@/components/Header";
import MapPicker, { type LatLng } from "@/components/MapPicker";
import { ApiError } from "@/lib/api";
import { saveCtx } from "@/lib/devContext";
import { CATEGORIES } from "@/lib/stores";
import { registerMerchant } from "@/lib/endpoints";

export default function MerchantRegisterPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [phone, setPhone] = useState("");
  const [proof, setProof] = useState<string>("");
  const [category, setCategory] = useState("식당");
  const [pos, setPos] = useState<LatLng | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res = await registerMerchant({ name: name.trim(), address: address.trim(), ...(proof ? { revenueProof: proof } : {}) });
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
        <label className="block"><span className="label">업종</span><select className="field" value={category} onChange={(e) => setCategory(e.target.value)}>{CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.icon} {c.value}</option>)}</select></label>
        <label className="block"><span className="label">연락처</span><input className="field" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} /></label>
        <div className="card space-y-2 !p-3.5">
          <div>
            <span className="block text-sm font-semibold">캐시백 적립 대상 증빙</span>
            <span className="note">연매출 30억 미만 증빙 서류를 첨부하면 적립 대상 가맹점으로 자동 승인돼요. 서류 내용은 검증하지 않고, 첨부 여부로만 결정합니다.</span>
          </div>
          <input type="file" aria-label="증빙 서류 첨부" onChange={(e) => setProof(e.target.files?.[0]?.name ?? "")} className="block w-full text-[13px] file:mr-3 file:rounded-lg file:border-0 file:bg-blue-50 file:px-3 file:py-2 file:text-[13px] file:font-semibold file:text-blue-700" />
          <p role="status" className={`rounded-lg px-3 py-2 text-[13px] font-medium ${proof ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-800"}`}>
            {proof ? `증빙 첨부됨 (${proof}) → 적립 대상 가맹점으로 등록돼요` : "증빙이 없으면 적립 제외 가맹점으로 등록돼요"}
          </p>
        </div>
        <div>
          <span className="label">지도 (위치 선택)</span>
          <MapPicker value={pos} onChange={setPos} />
          <p className="note mt-1">{pos ? `선택한 위치 ${pos.lat.toFixed(5)}, ${pos.lng.toFixed(5)}` : "지도를 눌러 가맹점 위치를 선택하세요"}</p>
        </div>
        <p className="note">연락처, 업종, 위치는 이 기기에만 저장되고 서버로 전송되지 않아요.</p>
        {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-800">{error}</p>}
        <button type="submit" disabled={busy || !name.trim() || !address.trim()} className="btn btn-primary disabled:opacity-40">{busy ? "등록 중…" : "등록"}</button>
      </form>
    </>
  );
}
