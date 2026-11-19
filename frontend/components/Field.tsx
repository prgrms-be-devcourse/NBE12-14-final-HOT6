type Props = {
  label: string;
  type?: string;
  defaultValue?: string;
  placeholder?: string;
  readOnly?: boolean;
};

export default function Field({ label, type = "text", defaultValue, placeholder, readOnly }: Props) {
  return (
    <label className="block">
      <span className="label">{label}</span>
      <input
        className={`field ${readOnly ? "bg-zinc-100 text-zinc-500" : ""}`}
        type={type}
        defaultValue={defaultValue}
        placeholder={placeholder}
        readOnly={readOnly}
      />
    </label>
  );
}
