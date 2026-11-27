"use client";

import { useCallback, useState } from "react";
import NoticeEditModal from "@/components/NoticeEditModal";
import Pager from "@/components/Pager";
import { getAdminNotices, type AdminNotice } from "@/lib/adminApi";
import { mmdd, NOTICE_BADGE, NOTICE_LABEL } from "@/lib/cashbackApi";
import { ymd } from "@/lib/dates";
import { useLoad } from "@/lib/useLoad";

function homeText(item: AdminNotice, today: string) {
  if (item.status === "WITHDRAWN" || !item.showOnHome || !item.homeUntil) return "-";
  return item.homeUntil < today ? "종료" : `${mmdd(item.homeUntil)}까지`;
}

// 공지 관리: 지금까지의 공지를 보고 문구와 홈 팝업을 고친다. 공지는 요율·한도 변경과 적립 중단 때 만들어진다
export default function AdminNoticesPage() {
  const [page, setPage] = useState(0);
  const load = useLoad(() => getAdminNotices(page), [page]);
  const [selected, setSelected] = useState<AdminNotice | null>(null);
  const [saved, setSaved] = useState("");
  const close = useCallback(() => setSelected(null), []);
  const onSaved = () => {
    setSelected(null);
    setSaved("공지를 저장했어요");
    setTimeout(() => setSaved(""), 3000);
    load.reload();
  };
  const data = load.data;
  const today = ymd(new Date());

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <h1 className="text-2xl font-extrabold">공지 관리</h1>
      {saved && <p role="status" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white">{saved}</p>}
      <section className="card overflow-x-auto !p-5">
        {load.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{load.error}</p>}
        {!data && load.loading && <p role="status" className="note">공지를 불러오는 중…</p>}
        {data && data.content.length === 0 && <p className="note">아직 공지가 없어요.</p>}
        {data && data.content.length > 0 && (
          <table className="table">
            <thead><tr><th>등록일</th><th>종류</th><th>제목</th><th>홈 팝업</th><th>상태</th><th>수정일</th></tr></thead>
            <tbody>
              {data.content.map((item) => {
                const n = item.notice;
                const withdrawn = item.status === "WITHDRAWN";
                return (
                  <tr key={n.noticeId} onClick={() => setSelected(item)} className={`cursor-pointer hover:bg-slate-50 ${withdrawn ? "text-slate-400" : ""}`}>
                    <td className="whitespace-nowrap tabular-nums">{n.createdAt.slice(0, 10)}</td>
                    <td className="whitespace-nowrap"><span className={`badge ${NOTICE_BADGE[n.type]}`}>{NOTICE_LABEL[n.type]}</span></td>
                    <td>
                      {/* 줄 어디를 눌러도 열리고, 키보드는 이 버튼으로 연다 */}
                      <button type="button" className="break-words text-left font-medium text-brand-600 underline">{n.title}</button>
                    </td>
                    <td className="whitespace-nowrap tabular-nums">{homeText(item, today)}</td>
                    <td className="whitespace-nowrap"><span className={`badge ${withdrawn ? "badge-bad" : "badge-ok"}`}>{withdrawn ? "예약 취소" : "게시 중"}</span></td>
                    <td className="whitespace-nowrap tabular-nums">{n.updatedAt ? mmdd(n.updatedAt) : "-"}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        {data && <Pager page={page} totalPages={data.totalPages} onChange={setPage} />}
        <p className="note mt-2">줄을 누르면 공지를 고칠 수 있어요. 새 공지는 정책 관리에서 변경할 때와 적립 중단 때 만들어져요.</p>
      </section>
      {selected && <NoticeEditModal key={selected.notice.noticeId} item={selected} onClose={close} onSaved={onSaved} />}
    </div>
  );
}
