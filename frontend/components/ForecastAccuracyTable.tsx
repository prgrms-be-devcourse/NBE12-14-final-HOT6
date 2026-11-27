"use client";

import { useState } from "react";
import { getForecastAccuracy, md } from "@/lib/adminApi";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

const SHORT = 7;
const round = (v: number) => won(Math.round(v));

// 예측 오차: 지난 날의 하루 예상 지급액과 실제 확보액을 나란히 보여 준다
export default function ForecastAccuracyTable() {
  const load = useLoad(() => getForecastAccuracy(), []);
  const [all, setAll] = useState(false);
  const data = load.data;
  const days = (all ? data?.days : data?.days.slice(0, SHORT)) ?? [];

  return (
    <div className="mt-5 space-y-2 border-t border-slate-200 pt-4">
      <h3 className="font-bold">예측 오차 <span className="note font-normal">최근 30일 · 하루 예상 수요(중앙)와 실제 확보액</span></h3>
      {load.error && <p role="alert" className="note">{load.error}</p>}
      {!data && load.loading && <p role="status" className="note">예측 오차를 불러오는 중…</p>}
      {data && data.days.length === 0 && <p className="note">아직 비교할 날이 없어요. 예측은 하루 한 번 저장하고 다음 날부터 비교해요.</p>}
      {data && data.averageError != null && (
        <>
          <p className="text-sm">
            비교한 {data.days.length}일의 평균 차이 <b className="tabular-nums">{won(data.averageError)}</b> · 실제가 예상 범위 안이었던 날{" "}
            <b className="tabular-nums">{data.withinRangeDays}/{data.days.length}일</b>
          </p>
          <div className="overflow-x-auto">
            <table className="table">
              <thead><tr><th>날짜</th><th>예상</th><th>예상 범위</th><th>실제</th><th>차이(실제 − 예상)</th><th>범위</th></tr></thead>
              <tbody>
                {days.map((d) => (
                  <tr key={d.date}>
                    <td className="whitespace-nowrap">{md(d.date)}</td>
                    <td className="tabular-nums">{round(d.predictedDemand)}</td>
                    <td className="whitespace-nowrap tabular-nums">{round(d.slowDemand)} ~ {round(d.fastDemand)}</td>
                    <td className="tabular-nums">{won(d.actualAmount)}</td>
                    <td className="tabular-nums">{d.difference > 0 ? "+" : ""}{won(d.difference)}</td>
                    <td><span className={`badge ${d.withinRange ? "badge-ok" : "badge-warn"}`}>{d.withinRange ? "안" : "밖"}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data.days.length > SHORT && (
            <button type="button" aria-expanded={all} onClick={() => setAll(!all)} className="btn btn-outline btn-sm !w-auto">{all ? "접기" : `전체 ${data.days.length}일 보기`}</button>
          )}
        </>
      )}
    </div>
  );
}
