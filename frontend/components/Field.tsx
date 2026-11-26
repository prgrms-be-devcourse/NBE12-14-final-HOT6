import { useId, useState, type ReactNode, type SyntheticEvent } from "react";

// 복사·잘라내기·붙여넣기·끌어다 놓기를 막아 직접 입력하게 한다.
// 회원가입 화면에만 쓴다. 로그인 화면은 비밀번호 관리 도구를 사용하는 사용자의 편의성을 위해 막지 않는다.
const block = (e: SyntheticEvent) => e.preventDefault();
const NO_CLIPBOARD = { onCopy: block, onCut: block, onPaste: block, onDrop: block };

type Props = {
  label: string;
  type?: string;
  defaultValue?: string;
  placeholder?: string;
  readOnly?: boolean;
  // 아래는 입력값을 화면이 직접 다룰 때만 쓴다 (value 를 주면 제어 입력)
  value?: string;
  onChange?: (value: string) => void;
  inputMode?: "numeric" | "tel" | "email" | "text";
  autoComplete?: string;
  maxLength?: number;
  hint?: string; // 입력칸 아래 안내 문구
  error?: string; // 입력칸 아래 오류 문구
  noClipboard?: boolean; // 복사·붙여넣기를 막는다
  help?: ReactNode; // 입력칸 우측 상단의 물음표를 누르면 보여 줄 입력 규칙
};

export default function Field({
  label, type = "text", defaultValue, placeholder, readOnly, value, onChange, inputMode, autoComplete, maxLength, hint, error, noClipboard, help,
}: Props) {
  const [helpOpen, setHelpOpen] = useState(false);
  const helpId = useId();
  const field = (
    <label className="block">
      <span className="label">{label}</span>
      <input
        className={`field ${readOnly ? "bg-zinc-100 text-zinc-500" : ""}`}
        type={type}
        defaultValue={value === undefined ? defaultValue : undefined}
        value={value}
        onChange={onChange ? (e) => onChange(e.target.value) : undefined}
        placeholder={placeholder}
        readOnly={readOnly}
        inputMode={inputMode}
        autoComplete={autoComplete}
        maxLength={maxLength}
        aria-invalid={error ? true : undefined}
        {...(noClipboard ? NO_CLIPBOARD : {})}
      />
      {error ? <span role="alert" className="mt-1 block text-[12px] font-medium text-red-600">{error}</span>
        : hint ? <span className="note mt-1 block">{hint}</span> : null}
    </label>
  );
  if (!help) return field;
  // 버튼을 label 안에 두면 라벨이 버튼에 연결되므로 바깥에 겹쳐 놓는다
  return (
    <div className="relative">
      {field}
      <button
        type="button"
        onClick={() => setHelpOpen((open) => !open)}
        aria-expanded={helpOpen}
        aria-controls={helpId}
        aria-label={`${label} 입력 규칙 보기`}
        className="absolute -top-0.5 right-0 flex h-5 w-5 items-center justify-center rounded-full border border-slate-300 text-[11px] font-bold text-slate-500 hover:bg-slate-100"
      >
        ?
      </button>
      <div id={helpId} hidden={!helpOpen} className="card-dash mt-1">{help}</div>
    </div>
  );
}
