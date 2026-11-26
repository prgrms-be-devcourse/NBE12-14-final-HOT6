"use client";

import SignupPinStep from "@/components/SignupPinStep";
import { signup } from "@/lib/userApi";

export default function PinSetupPage() {
  return <SignupPinStep signupHref="/signup" doneHref="/login" request={signup} />;
}
