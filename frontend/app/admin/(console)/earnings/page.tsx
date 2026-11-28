"use client";

import { Suspense, useState } from "react";
import EarningList from "@/components/EarningList";
import EarningStats from "@/components/EarningStats";
import PayoutBatch from "@/components/PayoutBatch";
import Tabs, { useTab, type TabItem } from "@/components/Tabs";

type TabId = "list" | "payouts" | "stats";
const TABS: TabItem<TabId>[] = [
  { id: "payouts", label: "지급 배치" },
  { id: "list", label: "적립 내역" },
  { id: "stats", label: "적립 통계" },
];

function EarningsBody() {
  const [tab, setTab] = useTab(TABS);
  const [ran, setRan] = useState(0); // 지급 배치를 실행한 횟수
  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <h1 className="text-2xl font-extrabold">적립 관리</h1>
      <Tabs items={TABS} active={tab} onChange={setTab} label="적립 관리 묶음" />
      {/* 조회 조건과 실행 결과가 탭을 오가도 남도록 숨기기만 한다 */}
      <div hidden={tab !== "list"}><EarningList refresh={ran} /></div>
      <div hidden={tab !== "payouts"}><PayoutBatch onRan={() => setRan((n) => n + 1)} /></div>
      {/* 통계는 바뀐 값을 보이도록 열 때마다 새로 읽는다 */}
      {tab === "stats" && <EarningStats />}
    </div>
  );
}

export default function AdminEarningsPage() {
  return (
    <Suspense fallback={null}>
      <EarningsBody />
    </Suspense>
  );
}
