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
};

export default function Field({
  label, type = "text", defaultValue, placeholder, readOnly, value, onChange, inputMode, autoComplete, maxLength, hint, error,
}: Props) {
  return (
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
      />
      {error ? <span role="alert" className="mt-1 block text-[12px] font-medium text-red-600">{error}</span>
        : hint ? <span className="note mt-1 block">{hint}</span> : null}
    </label>
  );
}
