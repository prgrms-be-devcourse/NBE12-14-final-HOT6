"use client";

import { useEffect, useState } from "react";
import { DEFAULT_CTX, loadCtx, saveCtx, type DevContext } from "@/lib/devContext";

type NumKey = "ownerId" | "walletId" | "merchantId";
const FIELDS: [NumKey, string][] = [
  ["ownerId", "ownerId (가맹점 주인)"],
  ["walletId", "walletId"],
  ["merchantId", "merchantId"],
];

export default function DevContextPanel() {
  const [ctx, setCtx] = useState<DevContext>(DEFAULT_CTX);
  // eslint-disable-next-line react-hooks/set-state-in-effect -- 브라우저 저장소 값은 마운트 후에 읽는다
  useEffect(() => setCtx(loadCtx()), []);
  const change = (k: NumKey, v: string) => {
    const n = Number(v.replace(/\D/g, "")) || 0;
    setCtx((c) => ({ ...c, [k]: n }));
    saveCtx({ [k]: n });
  };
  const changeToken = (v: string) => {
    const t = v.trim();
    setCtx((c) => ({ ...c, qrToken: t }));
    saveCtx({ qrToken: t });
  };
  return (
    <section className="card mt-6">
      <h2 className="font-semibold">개발용 식별자</h2>
      <p className="note mt-0.5">인증 API 연동 전까지 로그인 사용자 대신 요청에 넣는 값입니다. 이 브라우저에만 저장됩니다.</p>
      <div className="mt-3 grid gap-3 sm:grid-cols-3">
        {FIELDS.map(([k, l]) => (
          <label key={k} className="block"><span className="label">{l}</span>
            <input className="field" inputMode="numeric" value={ctx[k]} onChange={(e) => change(k, e.target.value)} /></label>
        ))}
      </div>
      <label className="mt-3 block"><span className="label">qrToken (가맹점 QR 생성 시 자동 저장)</span>
        <input className="field font-mono text-sm" value={ctx.qrToken ?? ""} onChange={(e) => changeToken(e.target.value)} placeholder="QR 생성 응답의 token" /></label>
    </section>
  );
}
