"use client";

import Link from "next/link";
import { useState } from "react";
import Header from "@/components/Header";
import MapPicker, { type LatLng } from "@/components/MapPicker";
import RegisterGate from "@/components/RegisterGate";
import { ApiError } from "@/lib/api";
import { saveCtx } from "@/lib/devContext";
import { PHONE_RULE } from "@/lib/signupDraft";
import { CATEGORIES } from "@/lib/stores";
import { registerMerchant } from "@/lib/endpoints";

// 로그인한 이용자만 등록할 수 있다(RegisterGate). 로그인하지 않았으면 이용자 로그인으로 보낸다
export default function MerchantRegisterPage() {
  return (
    <>
      <Header title="가맹점 등록" back="/merchant/login" />
      <RegisterGate>
        <RegisterForm />
      </RegisterGate>
    </>
  );
}

function RegisterForm() {
  const [done, setDone] = useState(false);
  const [needLogin, setNeedLogin] = useState(false); // 등록 중 401(로그인 만료·없음)
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [phone, setPhone] = useState("");
  const [proof, setProof] = useState<string>("");
  const [category, setCategory] = useState("식당");
  const [pos, setPos] = useState<LatLng | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [phoneTouched, setPhoneTouched] = useState(false);
  const phoneInvalid = phone !== "" && !PHONE_RULE.test(phone); // 연락처는 비워도 되지만, 입력하면 회원가입과 같은 규칙을 따른다

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (phoneInvalid) return;
    setError("");
    setNeedLogin(false);
    setBusy(true);
    try {
      const res = await registerMerchant({ name: name.trim(), address: address.trim(), ...(proof ? { revenueProof: proof } : {}) });
      saveCtx({ merchantName: res.name, phone: phone.trim(), category, merchantAddress: res.address, eligible: res.cashbackEligible, ...(pos ? { lat: pos.lat, lng: pos.lng } : {}) });
      setDone(true);
    } catch (err) {
      // 로그인이 풀렸거나 없는 경우: 말없이 로그인 화면으로 보내지 않고 이유를 알려 준다
      if (err instanceof ApiError && err.status === 401) {
        setNeedLogin(true);
        setError("로그인이 필요해요. 로그인한 뒤 다시 등록해 주세요");
        return;
      }
      setError(err instanceof ApiError ? err.message : "알 수 없는 오류가 발생했습니다");
    } finally {
      setBusy(false);
    }
  };

  // 등록하면 회원이 점주(OWNER)로 바뀌지만, 이미 받은 토큰은 옛 역할이라 다시 로그인해야 점주 기능을 쓸 수 있다
  if (done) {
    return (
      <>
        <div className="space-y-4 px-5 pb-5">
          <section className="card text-center">
            <div className="text-lg font-bold">가맹점이 등록되었어요</div>
            <p className="note mt-2">등록한 회원은 점주가 돼요. QR 생성과 정산 조회 같은 점주 기능은 다시 로그인한 뒤부터 쓸 수 있어요.</p>
          </section>
          <Link href="/merchant/login" className="btn btn-primary">다시 로그인하기</Link>
        </div>
      </>
    );
  }

  return (
    <>
      <form onSubmit={submit} className="space-y-3 px-5 pb-5">
        <label className="block"><span className="label">가맹점명</span><input className="field" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} /></label>
        <label className="block"><span className="label">주소</span><input className="field" required maxLength={200} value={address} onChange={(e) => setAddress(e.target.value)} /></label>
        <label className="block"><span className="label">업종</span><select className="field" value={category} onChange={(e) => setCategory(e.target.value)}>{CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.icon} {c.value}</option>)}</select></label>
        <label className="block"><span className="label">연락처</span>
          <input className="field" type="tel" inputMode="numeric" autoComplete="tel" placeholder="01012345678" maxLength={11} value={phone} aria-invalid={phoneTouched && phoneInvalid ? true : undefined} onChange={(e) => setPhone(e.target.value.replace(/\D/g, "").slice(0, 11))} onBlur={() => setPhoneTouched(true)} />
          <span className={`note mt-1 block ${phoneTouched && phoneInvalid ? "!text-red-600" : ""}`}>{phoneTouched && phoneInvalid ? "하이픈 없이 숫자만 입력해 주세요 (예: 01012345678)" : "하이픈 없이 숫자만 입력해 주세요"}</span>
        </label>
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
        {needLogin && <Link href="/login?next=/merchant/register" className="btn btn-outline">로그인하러 가기</Link>}
        <button type="submit" disabled={busy || !name.trim() || !address.trim() || phoneInvalid} className="btn btn-primary disabled:opacity-40">{busy ? "등록 중…" : "등록"}</button>
      </form>
    </>
  );
}
