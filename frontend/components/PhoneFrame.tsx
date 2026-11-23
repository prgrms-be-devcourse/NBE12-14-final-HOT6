export default function PhoneFrame({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative mx-auto flex min-h-screen w-full max-w-[420px] flex-col bg-white sm:my-6 sm:min-h-[780px] sm:overflow-hidden sm:rounded-[30px] sm:border sm:border-slate-300 sm:shadow-xl">
      {children}
    </div>
  );
}
