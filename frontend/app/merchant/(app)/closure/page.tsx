"use client";

import { useState } from "react";
import Header from "@/components/Header";
import MerchantClosedNotice from "@/components/MerchantClosedNotice";
import { ApiError } from "@/lib/api";
import { getClosurePreview, requestClosure } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

// 가맹점 해지. 신청 전에는 정산 현황과 신청 가능 여부를 보여 주고, 신청한 뒤에는 상태 안내만 보여 준다
export default function MerchantClosurePage() {
  const preview = useLoad(() => getClosurePreview(), []);
  const [agreed, setAgreed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const submit = async () => {
    setBusy(true);
    setError("");
    try {
      await requestClosure();
      preview.reload();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "해지를 신청하지 못했어요");
      preview.reload();
    } finally {
      setBusy(false);
    }
  };

  const p = preview.data;
  return (
    <>
      <Header title="가맹점 해지" back="/merchant/edit" />
      <div className="space-y-3 px-5 pb-5">
        {preview.loading && !p && <p role="status" className="note text-center">불러오는 중이에요…</p>}
        {preview.error && !p && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">{preview.error}</p>}

        {p && p.status !== "ACTIVE" && <MerchantClosedNotice status={p.status} />}

        {p && p.status === "ACTIVE" && (
          <>
            <section className="card space-y-2">
              <div className="font-semibold">정산 현황</div>
              <Row label="아직 지급되지 않은 정산금" value={won(p.unpaidAmount)} />
              <Row label="그중 갚을 금액에 쓸 수 있는 정산금" value={won(p.offsettableAmount)} />
              <Row label="취소로 쌓여 갚아야 하는 금액" value={won(p.carryDebt)} danger={p.carryDebt > 0} />
              <Row label="내 계좌 잔액 (참고)" value={won(p.accountBalance)} />
              <p className="note">계좌 잔액은 내 돈이라 갚을 금액에 쓰지 않아요. 갚을 금액은 아직 지급되지 않은 정산금에서 빼요.</p>
            </section>

            {p.closable ? (
              <section className="card space-y-3">
                <div className="font-semibold">해지 전에 꼭 확인해 주세요</div>
                <ul className="list-disc space-y-1 pl-5 text-[13px] text-slate-700">
                  <li>신청하면 바로 QR 생성과 결제가 막혀요.</li>
                  <li>신청은 철회할 수 없어요.</li>
                  <li>신청 이후 정산이 모두 완료되면 해지가 완료됩니다. 남은 정산금은 내 계좌로 들어와요.</li>
                  <li>해지한 뒤에도 정산과 결제 내역은 볼 수 있어요.</li>
                </ul>
                <label className="flex items-center gap-2 text-[13px]">
                  <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />
                  위 내용을 확인했어요
                </label>
                {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">{error}</p>}
                <button type="button" onClick={submit} disabled={!agreed || busy} className="btn btn-danger disabled:opacity-40">{busy ? "신청 중…" : "가맹점 해지 신청"}</button>
              </section>
            ) : (
              <section className="card" role="alert">
                <div className="font-semibold text-red-700">지금은 해지를 신청할 수 없어요</div>
                <p className="note mt-1">갚아야 하는 금액이 정산금보다 {won(p.shortfall)} 많아요. 매출로 정산금이 쌓이면 다시 신청할 수 있어요.</p>
              </section>
            )}
          </>
        )}
      </div>
    </>
  );
}

function Row({ label, value, danger = false }: { label: string; value: string; danger?: boolean }) {
  return (
    <div className="flex items-center justify-between text-[14px]">
      <span className="text-slate-600">{label}</span>
      <span className={`font-semibold tabular-nums ${danger ? "text-red-600" : ""}`}>{value}</span>
    </div>
  );
}
