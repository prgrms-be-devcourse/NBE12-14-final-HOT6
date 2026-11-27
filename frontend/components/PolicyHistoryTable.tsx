"use client";

import { Fragment, useState } from "react";
import PolicyBasisDetail from "@/components/PolicyBasisDetail";
import { getPolicyHistory, getSettingHistory, type PolicyHistory } from "@/lib/adminApi";
import { LIMIT_NAME, pctText } from "@/lib/cashbackApi";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

const SIZE = 20;
type Row = { id: string; kind: "rate" | "limit"; at: string; item: string; change: string; from: string; memo: string; detail?: PolicyHistory };
const FILTERS = [
  { id: "all", label: "전체" },
  { id: "rate", label: "적립률" },
  { id: "limit", label: "한도" },
] as const;
const dateTime = (iso: string) => iso.slice(0, 16).replace("T", " ");

// 요율 이력과 한도 이력을 합친 최근 변경 목록. 예약한 변경은 적용된 뒤에 나타난다
export default function PolicyHistoryTable() {
  const [filter, setFilter] = useState<(typeof FILTERS)[number]["id"]>("all");
  const load = useLoad(async () => {
    const [rates, limits] = await Promise.all([getPolicyHistory(0, SIZE), getSettingHistory(0, SIZE)]);
    const rows: Row[] = [
      ...rates.content.map((h): Row => ({
        id: `rate-${h.historyId}`, kind: "rate", at: h.changedAt, item: "적립률",
        change: `${h.beforeRate != null ? `${pctText(h.beforeRate)} → ` : ""}${pctText(h.afterRate)}`,
        from: h.afterStartsAt ?? h.beforeStartsAt ?? "", memo: h.action === "CREATE" ? "구간 생성" : h.reason ? "예약 수정" : "구간 변경",
        detail: h.reason || h.basis ? h : undefined,
      })),
      ...limits.content.map((h): Row => ({
        id: `limit-${h.historyId}`, kind: "limit", at: h.changedAt, item: LIMIT_NAME[h.key] ?? h.description,
        change: `${won(h.previousValue)} → ${won(h.value)}`, from: h.changedAt, memo: h.reset ? "기본값으로 되돌림" : "한도 변경",
      })),
    ];
    return rows.sort((a, b) => b.at.localeCompare(a.at));
  }, []);
  const [openId, setOpenId] = useState<string | null>(null); // 사유와 근거를 펼친 행
  const rows = load.data?.filter((r) => filter === "all" || r.kind === filter).slice(0, SIZE);

  return (
    <section className="card space-y-3 overflow-x-auto !p-5">
      <div className="flex flex-wrap gap-2" role="group" aria-label="이력 종류">
        {FILTERS.map((f) => (
          <button key={f.id} type="button" aria-pressed={filter === f.id} onClick={() => setFilter(f.id)} className={`chip ${filter === f.id ? "chip-on" : ""}`}>{f.label}</button>
        ))}
      </div>
      {load.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{load.error}</p>}
      {!load.data && load.loading && <p role="status" className="note">변경 이력을 불러오는 중…</p>}
      {rows && rows.length === 0 && <p className="note">변경 이력이 없어요.</p>}
      {rows && rows.length > 0 && (
        <table className="table">
          <thead><tr><th>일시</th><th>항목</th><th>변경</th><th>적용 시작</th><th>비고</th></tr></thead>
          <tbody>
            {rows.map((h) => (
              <Fragment key={h.id}>
                <tr>
                  <td className="whitespace-nowrap">{dateTime(h.at)}</td>
                  <td className="whitespace-nowrap">{h.item}</td>
                  <td className="whitespace-nowrap tabular-nums">{h.change}</td>
                  <td className="whitespace-nowrap">{dateTime(h.from)}</td>
                  <td className="whitespace-nowrap">
                    {h.memo}
                    {h.detail && (
                      <button type="button" aria-expanded={openId === h.id} onClick={() => setOpenId(openId === h.id ? null : h.id)} className="ml-2 font-medium text-brand-600 underline">
                        {openId === h.id ? "접기" : "사유·근거"}
                      </button>
                    )}
                  </td>
                </tr>
                {h.detail && openId === h.id && (
                  <tr><td colSpan={5}><PolicyBasisDetail history={h.detail} /></td></tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      )}
      <p className="note">최근 {SIZE}건까지 보여 줘요. 예약한 변경은 적용된 뒤에 나타나요.</p>
    </section>
  );
}
