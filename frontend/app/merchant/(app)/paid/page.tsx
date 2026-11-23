"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import Header from "@/components/Header";
import { loadCtx } from "@/lib/devContext";
import { getQrStatus } from "@/lib/endpoints";
import type { QrStatus, QrStatusResponse } from "@/lib/types";

const LABEL: Record<QrStatus, string> = { WAITING: "대기중", USED: "결제완료", EXPIRED: "만료" };
const ORDER: QrStatus[] = ["WAITING", "USED", "EXPIRED"];
const POLL_MS = 2000;

const fmtTime = (iso: string) => iso.slice(11, 16);

// 가장 최근에 만든 QR 의 상태를 2초마다 확인한다. 결제 상세는 결제 내역에서 본다
export default function MerchantPaidPage() {
  const [token, setToken] = useState("");
  const [res, setRes] = useState<QrStatusResponse | null>(null);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 브라우저 저장소 값은 마운트 후에 읽는다
    setToken(loadCtx().qrToken ?? "");
  }, []);

  const status = res?.status;
  useEffect(() => {
    if (!token || status === "USED" || status === "EXPIRED") return;
    let alive = true;
    const check = () => {
      getQrStatus(token)
        .then((r) => {
          if (alive) setRes(r);
        })
        .catch(() => {});
    };
    check();
    const id = setInterval(check, POLL_MS);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, [token, status]);

  return (
    <>
      <Header title="QR 결제 완료 확인" />
      <div className="space-y-4 px-5 pb-5">
        <div>
          <span className="label">QR</span>
          <div className="flex gap-2">
            {ORDER.map((s) => <span key={s} className={`chip flex-1 text-center ${status === s ? "chip-on" : ""}`}>{LABEL[s]}</span>)}
          </div>
        </div>
        <section className="card text-center">
          {!token && <p className="text-sm text-zinc-500">아직 만든 QR이 없어요. 새 QR을 만들어 주세요.</p>}
          {token && !res && <p role="status" className="text-sm text-zinc-500">QR 상태를 확인하는 중이에요…</p>}
          {res?.status === "USED" && <div className="amt-in text-xl font-bold">결제가 완료됐어요</div>}
          {res?.status === "WAITING" && <div className="text-sm text-zinc-600">손님의 결제를 기다리고 있어요 · {fmtTime(res.expiresAt)}까지 유효</div>}
          {res?.status === "EXPIRED" && <div className="text-sm text-zinc-600">QR 유효시간이 지났어요. 새 QR을 만들어 주세요.</div>}
          {token && <p className="mt-3 truncate font-mono text-xs text-zinc-400" title={token}>{token}</p>}
        </section>
        <div className="flex gap-2.5"><Link href="/merchant/qr" className="btn btn-primary">새 QR 생성</Link><Link href="/merchant/payments" className="btn btn-outline">결제 내역 보기</Link></div>
        <p className="note text-center">QR은 한 번 결제되면 다시 쓸 수 없어요</p>
      </div>
    </>
  );
}
