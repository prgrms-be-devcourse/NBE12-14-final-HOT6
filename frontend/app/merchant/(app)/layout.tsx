import MerchantNav from "@/components/MerchantNav";
import MerchantStrip from "@/components/MerchantStrip";
import OwnerPinGate from "@/components/OwnerPinGate";

export default function MerchantAppLayout({ children }: { children: React.ReactNode }) {
  return (
    <OwnerPinGate>
      <MerchantStrip />
      <div className="flex flex-1 flex-col">{children}</div>
      <MerchantNav />
    </OwnerPinGate>
  );
}
