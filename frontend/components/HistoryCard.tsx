type Props = {
  title: string;
  amount: string;
  amountClass?: string;
  detail: string;
  badge: string;
  badgeClass?: string;
  onClick?: () => void; // 있으면 누를 수 있는 카드(결제 영수증 열기)
};

// 내역 한 건: 윗줄은 이름과 금액, 아랫줄은 시각과 상태 배지
export default function HistoryCard({ title, amount, amountClass = "", detail, badge, badgeClass = "", onClick }: Props) {
  const body = (
    <>
      <div className="flex items-center justify-between">
        <span className="font-medium">{title}</span>
        <span className={`font-semibold ${amountClass}`}>{amount}</span>
      </div>
      <div className="mt-0.5 flex items-center justify-between">
        <span className="note">{detail}</span>
        <span className={`badge ${badgeClass}`}>{badge}</span>
      </div>
    </>
  );
  return (
    <li>
      {onClick ? (
        <button type="button" onClick={onClick} className="card !p-3.5 w-full text-left">{body}</button>
      ) : (
        <div className="card !p-3.5">{body}</div>
      )}
    </li>
  );
}
