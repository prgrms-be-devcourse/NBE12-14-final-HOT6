"use client";

import { useState } from "react";
import { ApiError } from "@/lib/api";
import { submitRevenueProof } from "@/lib/endpoints";

// 적립 제외 가맹점이 연매출 30억 미만 증빙 서류를 나중에 제출한다. 파일은 올리지 않고 이름만 보낸다(서류 검증은 하지 않는다).
export default function RevenueProofCard({ onApproved }: { onApproved: () => void }) {
  const [proof, setProof] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const submit = async () => {
    setBusy(true);
    setError("");
    try {
      await submitRevenueProof(proof);
      onApproved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "알 수 없는 오류가 발생했습니다");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card space-y-2 !p-3.5">
      <div>
        <span className="block text-sm font-semibold">캐시백 적립 대상 증빙</span>
        <span className="note">연매출 30억 미만 증빙 서류를 첨부하면 적립 대상 가맹점이 돼요. 제출한 뒤의 결제부터 적용돼요.</span>
      </div>
      <input type="file" aria-label="증빙 서류 첨부" onChange={(e) => setProof(e.target.files?.[0]?.name ?? "")} className="block w-full text-[13px] file:mr-3 file:rounded-lg file:border-0 file:bg-blue-50 file:px-3 file:py-2 file:text-[13px] file:font-semibold file:text-blue-700" />
      {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-800">{error}</p>}
      <button type="button" onClick={submit} disabled={busy || !proof} className="btn btn-primary disabled:opacity-40">{busy ? "제출 중…" : "증빙 제출"}</button>
    </div>
  );
}
