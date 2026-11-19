import MerchantNav from "@/components/MerchantNav";
import MerchantStrip from "@/components/MerchantStrip";

export default function MerchantAppLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <MerchantStrip />
      <div className="flex flex-1 flex-col">{children}</div>
      <MerchantNav />
    </>
  );
}
