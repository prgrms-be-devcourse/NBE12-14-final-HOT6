import BarChart from "@/components/BarChart";
import ConfirmButton from "@/components/ConfirmButton";
import { weekdays, policyHistory } from "@/lib/mock";

const KPI = [
  { k: "집행 완료", v: "4,200,000원", c: "text-blue-600", bar: "bg-blue-500", tag: "완료", tagCls: "badge-info" },
  { k: "예약 · 확보", v: "1,800,000원", c: "text-amber-600", bar: "bg-amber-400", tag: "확보", tagCls: "badge-warn" },
  { k: "남은 예산", v: "4,000,000원", c: "text-emerald-600", bar: "bg-emerald-500", tag: "남음", tagCls: "badge-ok" },
];

export default function AdminDashboardPage() {
  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">운영자 대시보드 <span className="text-base font-normal text-zinc-500">2026년 11월</span></h1>
        <ConfirmButton label="긴급 중단" message="적립이 즉시 중단돼요. 진행할까요?" doneLabel="긴급 중단됨" />
      </div>
      <div className="grid gap-3 sm:grid-cols-3">
        {KPI.map((x) => <div key={x.k} className="card relative overflow-hidden"><span className={`absolute inset-y-0 left-0 w-1 ${x.bar}`} /><div className="flex items-center justify-between"><span className="note">{x.k}</span><span className={`badge ${x.tagCls}`}>{x.tag}</span></div><div className={`mt-1 text-xl font-bold ${x.c}`}>{x.v}</div></div>)}
      </div>
      <div role="alert" className="rounded-xl border border-amber-500 bg-amber-50 px-4 py-3 text-sm text-amber-900">
        <b>! </b>남은 예산이 대비금 기준 이하입니다 — 공지 후 중단 검토
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <section className="card"><h2 className="mb-3 font-semibold">주간 소진 예측 <span className="note">(7일 수요 기반)</span></h2>
          <BarChart label="주간 소진 예측" labels={weekdays} reserveLine={100} series={[{ name: "예상 잔액(만원)", color: "#3b82f6", values: [380, 330, 270, 210, 150, 110, 70] }]} /></section>
        <section className="card"><h2 className="mb-3 font-semibold">요율별 예상 잔액 비교</h2>
          <BarChart label="요율별 예상 잔액" labels={weekdays} series={[{ name: "요율 1.5%", color: "#93c5fd", values: [390, 350, 310, 270, 230, 200, 170] }, { name: "요율 2.0%", color: "#3b82f6", values: [380, 330, 270, 210, 150, 110, 70] }, { name: "요율 2.5%", color: "#1e3a8a", values: [370, 300, 220, 140, 60, 10, 0] }]} /></section>
      </div>
      <section className="card"><h2 className="mb-3 font-semibold">예측 vs 실제 비교</h2>
        <BarChart label="예측 대비 실제" labels={weekdays} series={[{ name: "실제", color: "#10b981", values: [62, 58, 71, 66, 80, 54, 0] }, { name: "예측", color: "#cbd5e1", values: [60, 60, 65, 70, 75, 55, 50] }]} /></section>
      <section className="card overflow-x-auto"><h2 className="mb-2 font-semibold">정책 변경 이력</h2>
        <table className="table"><thead><tr><th>일자</th><th>요율</th><th>적용 기간</th><th>비고</th></tr></thead>
          <tbody>{policyHistory.map((r) => <tr key={r.date}><td>{r.date}</td><td>{r.rate}</td><td>{r.period}</td><td>{r.note}</td></tr>)}</tbody></table>
        <p className="note mt-2">※ B의 조회 API(예산·적립 시계열)를 받아 계산·비교</p></section>
    </div>
  );
}
