// 목록 아래 "이전 · n / 전체 · 다음". 한 쪽뿐이면 보이지 않는다
export default function Pager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-4 flex items-center justify-between">
      <button type="button" disabled={page === 0} onClick={() => onChange(page - 1)} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">이전</button>
      <span className="note">{page + 1} / {totalPages}</span>
      <button type="button" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">다음</button>
    </div>
  );
}
