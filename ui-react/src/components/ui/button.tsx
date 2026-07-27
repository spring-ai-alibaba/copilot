import React from "react";
import { Loader2 } from "lucide-react";
import { cn } from "@/utils/cn";

type ButtonVariant = "default" | "secondary" | "ghost" | "outline" | "destructive";
type ButtonSize = "sm" | "md" | "icon";

export type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
};

const variants: Record<ButtonVariant, string> = {
  default:
    "bg-foreground text-background hover:bg-foreground/85 disabled:bg-muted disabled:text-muted-foreground",
  secondary: "bg-muted text-foreground hover:bg-muted/75",
  ghost: "bg-transparent text-muted-foreground hover:bg-foreground/[0.06] hover:text-foreground",
  outline:
    "border border-border bg-background text-foreground hover:bg-muted/65 hover:border-foreground/15",
  destructive:
    "bg-destructive text-destructive-foreground hover:bg-destructive/90",
};

const sizes: Record<ButtonSize, string> = {
  sm: "h-8 rounded-lg px-3 text-xs",
  md: "h-9 rounded-lg px-3.5 text-sm",
  icon: "h-8 w-8 rounded-lg p-0",
};

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, children, variant = "default", size = "md", loading, disabled, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={cn(
        "inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap font-medium shadow-none outline-none transition-[background-color,border-color,color,transform,opacity] duration-150 active:scale-[0.97] focus-visible:ring-2 focus-visible:ring-ring/40 focus-visible:ring-offset-1 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-50",
        variants[variant],
        sizes[size],
        className,
      )}
      {...props}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
      {children}
    </button>
  );
});
