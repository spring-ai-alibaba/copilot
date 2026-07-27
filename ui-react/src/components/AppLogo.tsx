import { Braces } from "lucide-react";
import { cn } from "@/utils/cn";

export function AppLogo({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        "flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border border-foreground/[0.08] bg-foreground text-background shadow-sm",
        className,
      )}
      aria-hidden="true"
    >
      <Braces className="h-4 w-4" strokeWidth={2.2} />
    </div>
  );
}
